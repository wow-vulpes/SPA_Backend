package com.example.spa.movement;

import com.example.spa.catalog.persistence.LocationRepository;
import com.example.spa.catalog.persistence.ProductRepository;
import com.example.spa.movement.persistence.Movement;
import com.example.spa.movement.persistence.MovementAllocation;
import com.example.spa.movement.persistence.MovementAllocationRepository;
import com.example.spa.movement.persistence.MovementRepository;
import com.example.spa.stock.persistence.Batch;
import com.example.spa.stock.persistence.BatchRepository;
import com.example.spa.stock.persistence.InventoryPosition;
import com.example.spa.stock.persistence.InventoryPositionRepository;
import com.example.spa.stock.persistence.StockRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MovementService {
  private final ProductRepository products;
  private final LocationRepository locations;
  private final InventoryPositionRepository positions;
  private final BatchRepository batches;
  private final MovementRepository movements;
  private final MovementAllocationRepository allocations;
  private final StockRepository stock;
  private final Clock clock;

  @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 15)
  public MovementResult post(MovementCommand command) {
    LocalDate today = LocalDate.now(clock);
    MovementRules.validate(command, today);
    products
        .findById(command.sku())
        .orElseThrow(() -> MovementException.notFound("SKU_NOT_FOUND", "Товар не найден"));
    locations
        .findById(command.location())
        .orElseThrow(() -> MovementException.notFound("LOCATION_NOT_FOUND", "Объект не найден"));
    checkDocument(command.documentNumber());

    // Upsert serializes concurrent creation; the following statement sees the winner's commit.
    positions.insertIfAbsent(command.sku(), command.location(), command.operationDate());
    InventoryPosition position =
        positions.findForUpdate(command.sku(), command.location()).orElseThrow();
    checkDocument(command.documentNumber());
    movements
        .findFirstByPositionIdOrderByOperationDateDescIdDesc(position.getId())
        .ifPresent(
            last -> {
              if (command.operationDate().isBefore(last.getOperationDate())) {
                throw MovementException.invalid(
                    "OUT_OF_ORDER_DATE", "Дата раньше последней операции позиции");
              }
            });

    List<Allocation> plan =
        command.type() == MovementType.CONSUME
            ? FefoAllocator.allocate(
                stock.batchBalances(position.getId()).stream()
                    .map(
                        b ->
                            new FefoAllocator.BatchStock(
                                b.getBatchId(),
                                b.getReceivedOn(),
                                b.getExpiresOn(),
                                b.getQuantity()))
                    .toList(),
                command.quantity(),
                command.operationDate())
            : allocateSingleBatch(command, position.getId());

    Movement movement =
        movements.save(
            new Movement(
                position.getId(),
                command.operationDate(),
                command.type(),
                command.quantity(),
                command.documentNumber()));
    for (Allocation item : plan) {
      allocations.save(
          new MovementAllocation(
              movement.getId(), item.batchId(), position.getId(), item.quantityDelta()));
    }
    return new MovementResult(
        movement.getId(),
        command.sku(),
        command.location(),
        stock.physicalQuantity(position.getId()),
        stock.availableQuantity(position.getId(), today),
        plan);
  }

  private List<Allocation> allocateSingleBatch(MovementCommand command, Long positionId) {
    Batch batch =
        command.batchId() == null
            ? resolveReceiptBatch(command, positionId)
            : batches
                .findById(command.batchId())
                .orElseThrow(
                    () -> MovementException.notFound("BATCH_NOT_FOUND", "Партия не найдена"));
    if (!batch.getPositionId().equals(positionId)) {
      throw MovementException.invalid(
          "BATCH_POSITION_MISMATCH", "Партия принадлежит другому товару или объекту");
    }
    if (command.operationDate().isBefore(batch.getReceivedOn())) {
      throw MovementException.invalid(
          "BEFORE_RECEIPT_DATE", "Дата операции раньше поступления партии");
    }
    BigDecimal delta =
        command.type() == MovementType.WRITEOFF ? command.quantity().negate() : command.quantity();
    if (delta.signum() < 0) {
      BigDecimal available =
          stock.batchBalances(positionId).stream()
              .filter(b -> b.getBatchId().equals(batch.getId()))
              .map(b -> b.getQuantity())
              .findFirst()
              .orElse(BigDecimal.ZERO);
      if (delta.abs().compareTo(available) > 0) throw MovementException.insufficient(available);
    }
    return List.of(new Allocation(batch.getId(), delta));
  }

  private Batch resolveReceiptBatch(MovementCommand command, Long positionId) {
    MovementCommand.NewBatch data = command.batch();
    return batches
        .findByPositionIdAndBatchNumber(positionId, data.number())
        .map(
            existing -> {
              if (!existing.getExpiresOn().equals(data.expiresOn())
                  || existing.getUnitPrice().compareTo(data.unitPrice()) != 0
                  || !existing.getInvoiceNumber().equals(data.invoiceNumber())) {
                throw MovementException.invalid(
                    "BATCH_METADATA_MISMATCH", "Реквизиты существующей партии нельзя изменить");
              }
              return existing;
            })
        .orElseGet(
            () ->
                batches.save(
                    new Batch(
                        positionId,
                        data.number(),
                        command.operationDate(),
                        data.expiresOn(),
                        data.unitPrice(),
                        data.invoiceNumber())));
  }

  private void checkDocument(String document) {
    if (movements.findByDocumentNumber(document).isPresent())
      throw MovementException.duplicateDocument();
  }
}
