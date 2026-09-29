CREATE TABLE product (
    sku varchar(64) PRIMARY KEY,
    name varchar(255) NOT NULL,
    unit varchar(32) NOT NULL,
    CONSTRAINT ck_product_sku CHECK (sku = btrim(sku) AND sku <> ''),
    CONSTRAINT ck_product_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_product_unit CHECK (btrim(unit) <> '')
);

CREATE TABLE location (
    code varchar(64) PRIMARY KEY,
    name varchar(255) NOT NULL,
    CONSTRAINT ck_location_code CHECK (code = btrim(code) AND code <> ''),
    CONSTRAINT ck_location_name CHECK (btrim(name) <> '')
);

-- A stable lock target exists even when the position has no batches or movements.
CREATE TABLE inventory_position (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku varchar(64) NOT NULL REFERENCES product(sku),
    location_code varchar(64) NOT NULL REFERENCES location(code),
    registered_on date NOT NULL,
    CONSTRAINT uq_position_product_location UNIQUE (sku, location_code)
);
CREATE INDEX ix_position_location ON inventory_position(location_code);

CREATE TABLE batch (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    position_id bigint NOT NULL REFERENCES inventory_position(id),
    batch_number varchar(128) NOT NULL,
    received_on date NOT NULL,
    expires_on date NOT NULL,
    unit_price numeric(19,4) NOT NULL,
    invoice_number varchar(128) NOT NULL,
    CONSTRAINT uq_batch_number UNIQUE (position_id, batch_number),
    CONSTRAINT uq_batch_position UNIQUE (id, position_id),
    CONSTRAINT ck_batch_number CHECK (batch_number = btrim(batch_number) AND batch_number <> ''),
    CONSTRAINT ck_batch_invoice CHECK (invoice_number = btrim(invoice_number) AND invoice_number <> ''),
    CONSTRAINT ck_batch_price CHECK (unit_price >= 0 AND unit_price <> 'NaN'::numeric)
);
CREATE INDEX ix_batch_fefo ON batch(position_id, expires_on, received_on, id);

CREATE TABLE movement (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    position_id bigint NOT NULL REFERENCES inventory_position(id),
    operation_date date NOT NULL,
    operation_type varchar(16) NOT NULL,
    quantity numeric(19,6) NOT NULL,
    document_number varchar(128) NOT NULL,
    CONSTRAINT uq_movement_document UNIQUE (document_number),
    CONSTRAINT uq_movement_position UNIQUE (id, position_id),
    CONSTRAINT ck_movement_type CHECK (operation_type IN ('RECEIPT', 'CONSUME', 'WRITEOFF', 'RETURN', 'CORRECTION')),
    CONSTRAINT ck_movement_quantity CHECK (
        quantity <> 'NaN'::numeric AND
        ((operation_type = 'CORRECTION' AND quantity <> 0) OR
         (operation_type <> 'CORRECTION' AND quantity > 0))
    ),
    CONSTRAINT ck_movement_document CHECK (document_number = btrim(document_number) AND document_number <> '')
);
CREATE INDEX ix_movement_position_date ON movement(position_id, operation_date, id);
CREATE INDEX ix_movement_date ON movement(operation_date, id);

CREATE TABLE movement_allocation (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    movement_id bigint NOT NULL,
    batch_id bigint NOT NULL,
    position_id bigint NOT NULL,
    quantity_delta numeric(19,6) NOT NULL,
    CONSTRAINT fk_allocation_movement FOREIGN KEY (movement_id, position_id) REFERENCES movement(id, position_id),
    CONSTRAINT fk_allocation_batch FOREIGN KEY (batch_id, position_id) REFERENCES batch(id, position_id),
    CONSTRAINT uq_allocation_movement_batch UNIQUE (movement_id, batch_id),
    CONSTRAINT ck_allocation_delta CHECK (quantity_delta <> 0 AND quantity_delta <> 'NaN'::numeric)
);
CREATE INDEX ix_allocation_batch ON movement_allocation(batch_id);
CREATE INDEX ix_allocation_position ON movement_allocation(position_id);

COMMENT ON TABLE movement_allocation IS 'Only source of stock quantities; movement.quantity must not be added again.';
COMMENT ON COLUMN batch.unit_price IS 'RUB per product unit; historical receipt price.';
