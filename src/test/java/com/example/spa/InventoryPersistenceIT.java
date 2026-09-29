package com.example.spa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.spa.catalog.persistence.Location;
import com.example.spa.catalog.persistence.LocationRepository;
import com.example.spa.catalog.persistence.Product;
import com.example.spa.catalog.persistence.ProductRepository;
import com.example.spa.movement.MovementType;
import com.example.spa.movement.persistence.Movement;
import com.example.spa.movement.persistence.MovementAllocation;
import com.example.spa.movement.persistence.MovementAllocationRepository;
import com.example.spa.movement.persistence.MovementRepository;
import com.example.spa.stock.persistence.Batch;
import com.example.spa.stock.persistence.BatchBalance;
import com.example.spa.stock.persistence.BatchRepository;
import com.example.spa.stock.persistence.InventoryPosition;
import com.example.spa.stock.persistence.InventoryPositionRepository;
import com.example.spa.stock.persistence.StockRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional
class InventoryPersistenceIT {
  private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

  @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired ProductRepository products;
  @Autowired LocationRepository locations;
  @Autowired InventoryPositionRepository positions;
  @Autowired BatchRepository batches;
  @Autowired MovementRepository movements;
  @Autowired MovementAllocationRepository allocations;
  @Autowired StockRepository stock;
  @Autowired EntityManager entityManager;
  @Autowired JdbcTemplate jdbc;
  @Autowired Flyway flyway;
  private InventoryPosition position;

  @BeforeEach
  void createCatalog() {
    products.save(new Product("OIL-001", "Массажное масло", "л"));
    locations.save(new Location("MS-01", "Первый SPA"));
    position = positions.save(new InventoryPosition("OIL-001", "MS-01", TODAY.minusDays(100)));
    entityManager.flush();
  }

  @Test
  void migratesEmptyDatabaseAndValidatesHibernateMappings() {
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
    assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name in ('product', 'location', 'inventory_position', 'batch', 'movement', 'movement_allocation')",
                Integer.class))
        .isEqualTo(6);
  }

  @Test
  void persistsAndReloadsEntireLedgerWithoutLosingDecimalPrecision() {
    Batch batch = batch(position, "B-1", TODAY.plusDays(10));
    Movement movement = post(batch, MovementType.RECEIPT, "1.123456", "D-1");
    entityManager.flush();
    entityManager.clear();
    assertThat(products.findById("OIL-001").orElseThrow().getName()).isEqualTo("Массажное масло");
    assertThat(locations.findById("MS-01").orElseThrow().getName()).isEqualTo("Первый SPA");
    assertThat(
            positions.findBySkuAndLocationCode("OIL-001", "MS-01").orElseThrow().getRegisteredOn())
        .isEqualTo(TODAY.minusDays(100));
    assertThat(batches.findById(batch.getId()).orElseThrow().getUnitPrice())
        .isEqualByComparingTo("1259.0501");
    assertThat(movements.findByDocumentNumber("D-1").orElseThrow().getQuantity())
        .isEqualByComparingTo("1.123456");
    assertThat(movements.findById(movement.getId()).orElseThrow().getOperationType())
        .isEqualTo(MovementType.RECEIPT);
    assertThat(allocations.findAllByMovementIdOrderById(movement.getId()))
        .singleElement()
        .satisfies(a -> assertThat(a.getQuantityDelta()).isEqualByComparingTo("1.123456"));
    assertThat(stock.batchBalances(position.getId()))
        .singleElement()
        .satisfies(
            b -> {
              assertThat(b.getBatchNumber()).isEqualTo("B-1");
              assertThat(b.getInvoiceNumber()).isEqualTo("INV-B-1");
              assertThat(b.getQuantity()).isEqualByComparingTo("1.123456");
            });
  }

  @Test
  void sumsAllOperationTypesFromAllocationsOnly() {
    Batch batch = batch(position, "B-1", TODAY.plusDays(10));
    post(batch, MovementType.RECEIPT, "10.5", "D-1");
    post(batch, MovementType.CONSUME, "2", "D-2");
    post(batch, MovementType.WRITEOFF, "1.25", "D-3");
    post(batch, MovementType.RETURN, "0.25", "D-4");
    post(batch, MovementType.CORRECTION, "-0.5", "D-5");
    post(batch, MovementType.CORRECTION, "1", "D-6");
    entityManager.flush();
    assertThat(stock.physicalQuantity(position.getId())).isEqualByComparingTo("8");
  }

  @Test
  void splitMovementDoesNotDoubleCountHeaderQuantity() {
    Batch first = batch(position, "B-1", TODAY.plusDays(10));
    Batch second = batch(position, "B-2", TODAY.plusDays(20));
    post(first, MovementType.RECEIPT, "5", "D-1");
    post(second, MovementType.RECEIPT, "7", "D-2");
    Movement consume =
        movements.save(
            new Movement(
                position.getId(), TODAY, MovementType.CONSUME, new BigDecimal("6"), "D-3"));
    allocations.save(
        new MovementAllocation(
            consume.getId(), first.getId(), position.getId(), new BigDecimal("-5")));
    allocations.save(
        new MovementAllocation(
            consume.getId(), second.getId(), position.getId(), new BigDecimal("-1")));
    entityManager.flush();
    assertThat(stock.physicalQuantity(position.getId())).isEqualByComparingTo("6");
    assertThat(stock.batchBalances(position.getId()))
        .extracting(BatchBalance::getQuantity)
        .containsExactly(new BigDecimal("0.000000"), new BigDecimal("6.000000"));
  }

  @Test
  void expiryFiltersAvailabilityButDoesNotErasePhysicalStock() {
    post(batch(position, "EXPIRED", TODAY.minusDays(1)), MovementType.RECEIPT, "5", "D-1");
    post(batch(position, "TODAY", TODAY), MovementType.RECEIPT, "2", "D-2");
    post(batch(position, "FUTURE", TODAY.plusDays(1)), MovementType.RECEIPT, "3", "D-3");
    entityManager.flush();
    assertThat(stock.physicalQuantity(position.getId())).isEqualByComparingTo("10");
    assertThat(stock.availableQuantity(position.getId(), TODAY)).isEqualByComparingTo("5");
    assertThat(stock.availableQuantity(position.getId(), TODAY.plusDays(2)))
        .isEqualByComparingTo("0");
  }

  @Test
  void emptyPositionAndEmptyBatchHaveZeroStock() {
    assertThat(stock.physicalQuantity(position.getId())).isEqualByComparingTo("0");
    assertThat(stock.availableQuantity(position.getId(), TODAY)).isEqualByComparingTo("0");
    assertThat(stock.batchBalances(position.getId())).isEmpty();
    batch(position, "EMPTY", TODAY);
    entityManager.flush();
    assertThat(stock.batchBalances(position.getId()))
        .singleElement()
        .satisfies(b -> assertThat(b.getQuantity()).isEqualByComparingTo("0"));
  }

  @Test
  void isolatesProductsAndLocations() {
    locations.save(new Location("MS-02", "Второй SPA"));
    products.save(new Product("CREAM-001", "Крем", "кг"));
    InventoryPosition otherLocation =
        positions.save(new InventoryPosition("OIL-001", "MS-02", TODAY));
    InventoryPosition otherProduct =
        positions.save(new InventoryPosition("CREAM-001", "MS-01", TODAY));
    post(batch(position, "A", TODAY), MovementType.RECEIPT, "1", "D-1");
    post(batch(otherLocation, "A", TODAY), MovementType.RECEIPT, "10", "D-2");
    post(batch(otherProduct, "A", TODAY), MovementType.RECEIPT, "100", "D-3");
    entityManager.flush();
    assertThat(stock.physicalQuantity(position.getId())).isEqualByComparingTo("1");
    assertThat(stock.physicalQuantity(otherLocation.getId())).isEqualByComparingTo("10");
    assertThat(stock.physicalQuantity(otherProduct.getId())).isEqualByComparingTo("100");
  }

  @Test
  void ordersBatchesByExpiryThenReceiptDateThenId() {
    Batch later = batch(position, "LATER", TODAY.plusDays(1));
    Batch first = batch(position, "FIRST", TODAY);
    Batch tie = batch(position, "TIE", TODAY);
    Batch older =
        batches.save(
            new Batch(
                position.getId(), "OLDER", TODAY.minusDays(60), TODAY, BigDecimal.ONE, "INV"));
    entityManager.flush();
    assertThat(stock.batchBalances(position.getId()))
        .extracting(BatchBalance::getBatchId)
        .containsExactly(older.getId(), first.getId(), tie.getId(), later.getId());
  }

  @Test
  void rejectsDuplicatePosition() {
    rejects(
        "uq_position_product_location",
        () ->
            jdbc.update(
                "insert into inventory_position(sku, location_code, registered_on) values ('OIL-001', 'MS-01', ?)",
                TODAY));
  }

  @Test
  void rejectsDuplicateBatchWithinPosition() {
    batch(position, "B-1", TODAY);
    rejects("uq_batch_number", () -> batch(position, "B-1", TODAY));
  }

  @Test
  void rejectsDuplicateDocumentAcrossPositions() {
    post(batch(position, "B-1", TODAY), MovementType.RECEIPT, "1", "D-1");
    locations.save(new Location("MS-02", "Second"));
    InventoryPosition other = positions.save(new InventoryPosition("OIL-001", "MS-02", TODAY));
    rejects(
        "uq_movement_document",
        () ->
            movements.save(
                new Movement(other.getId(), TODAY, MovementType.RECEIPT, BigDecimal.ONE, "D-1")));
  }

  @ParameterizedTest
  @CsvSource({
    "RECEIPT,0",
    "RECEIPT,-1",
    "CONSUME,0",
    "CONSUME,-1",
    "WRITEOFF,-1",
    "RETURN,-1",
    "CORRECTION,0",
    "RECEIPT,NaN"
  })
  void rejectsInvalidMovementQuantities(String type, String quantity) {
    rejects(
        "ck_movement_quantity",
        () ->
            jdbc.update(
                "insert into movement(position_id, operation_date, operation_type, quantity, document_number) values (?, ?, ?, cast(? as numeric), 'BAD')",
                position.getId(),
                TODAY,
                type,
                quantity));
  }

  @Test
  void rejectsUnknownOperation() {
    rejects(
        "ck_movement_type",
        () ->
            jdbc.update(
                "insert into movement(position_id, operation_date, operation_type, quantity, document_number) values (?, ?, 'TRANSFER', 1, 'BAD')",
                position.getId(),
                TODAY));
  }

  @ParameterizedTest
  @CsvSource({"'   '", "' D-1 '"})
  void rejectsEmptyOrUntrimmedDocument(String document) {
    rejects(
        "ck_movement_document",
        () ->
            jdbc.update(
                "insert into movement(position_id, operation_date, operation_type, quantity, document_number) values (?, ?, 'RECEIPT', 1, ?)",
                position.getId(),
                TODAY,
                document));
  }

  @Test
  void rejectsUnknownProduct() {
    rejects(
        "inventory_position_sku_fkey",
        () ->
            jdbc.update(
                "insert into inventory_position(sku, location_code, registered_on) values ('UNKNOWN', 'MS-01', ?)",
                TODAY));
  }

  @Test
  void rejectsUnknownLocation() {
    rejects(
        "inventory_position_location_code_fkey",
        () ->
            jdbc.update(
                "insert into inventory_position(sku, location_code, registered_on) values ('OIL-001', 'UNKNOWN', ?)",
                TODAY));
  }

  @Test
  void rejectsNegativePrice() {
    rejects(
        "ck_batch_price",
        () ->
            batches.save(
                new Batch(position.getId(), "BAD", TODAY, TODAY, new BigDecimal("-1"), "INV")));
  }

  @Test
  void rejectsAllocationToDifferentPosition() {
    locations.save(new Location("MS-02", "Second"));
    InventoryPosition other = positions.save(new InventoryPosition("OIL-001", "MS-02", TODAY));
    Batch batch = batch(other, "OTHER", TODAY);
    Movement movement =
        movements.save(
            new Movement(position.getId(), TODAY, MovementType.RECEIPT, BigDecimal.ONE, "D-1"));
    rejects(
        "fk_allocation_batch",
        () ->
            allocations.save(
                new MovementAllocation(
                    movement.getId(), batch.getId(), position.getId(), BigDecimal.ONE)));
  }

  @Test
  void rejectsAllocationWithForgedMovementPosition() {
    locations.save(new Location("MS-02", "Second"));
    InventoryPosition other = positions.save(new InventoryPosition("OIL-001", "MS-02", TODAY));
    Batch batch = batch(other, "OTHER", TODAY);
    Movement movement =
        movements.save(
            new Movement(position.getId(), TODAY, MovementType.RECEIPT, BigDecimal.ONE, "D-1"));
    rejects(
        "fk_allocation_movement",
        () ->
            allocations.save(
                new MovementAllocation(
                    movement.getId(), batch.getId(), other.getId(), BigDecimal.ONE)));
  }

  @Test
  void rejectsDuplicateAllocation() {
    Batch batch = batch(position, "B-1", TODAY);
    Movement movement = post(batch, MovementType.RECEIPT, "1", "D-1");
    rejects(
        "uq_allocation_movement_batch",
        () ->
            allocations.save(
                new MovementAllocation(
                    movement.getId(), batch.getId(), position.getId(), BigDecimal.ONE)));
  }

  @Test
  void rejectsZeroAllocation() {
    Batch batch = batch(position, "B-1", TODAY);
    Movement movement =
        movements.save(
            new Movement(position.getId(), TODAY, MovementType.RECEIPT, BigDecimal.ONE, "D-1"));
    rejects(
        "ck_allocation_delta",
        () ->
            allocations.save(
                new MovementAllocation(
                    movement.getId(), batch.getId(), position.getId(), BigDecimal.ZERO)));
  }

  @Test
  void cannotDeleteBatchWithLedgerEntries() {
    Batch batch = batch(position, "B-1", TODAY);
    post(batch, MovementType.RECEIPT, "1", "D-1");
    entityManager.flush();
    rejects(
        "fk_allocation_batch", () -> jdbc.update("delete from batch where id = ?", batch.getId()));
  }

  @Test
  void exposesLockTargetEvenBeforeAnyBatchesExist() {
    assertThat(positions.findByIdForUpdate(position.getId())).isPresent();
    assertThat(stock.batchBalances(position.getId())).isEmpty();
    assertThat(positions.findByIdForUpdate(-1L)).isEmpty();
  }

  private Batch batch(InventoryPosition owner, String number, LocalDate expiry) {
    return batches.save(
        new Batch(
            owner.getId(),
            number,
            TODAY.minusDays(30),
            expiry,
            new BigDecimal("1259.0501"),
            "INV-" + number));
  }

  private Movement post(Batch batch, MovementType type, String quantity, String document) {
    BigDecimal amount = new BigDecimal(quantity);
    Movement movement =
        movements.save(new Movement(batch.getPositionId(), TODAY, type, amount, document));
    BigDecimal delta =
        type == MovementType.CONSUME || type == MovementType.WRITEOFF ? amount.negate() : amount;
    allocations.save(
        new MovementAllocation(movement.getId(), batch.getId(), batch.getPositionId(), delta));
    return movement;
  }

  private void rejects(String constraint, Runnable statement) {
    assertThatThrownBy(statement::run)
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasStackTraceContaining(constraint);
  }
}
