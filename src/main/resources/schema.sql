CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price DECIMAL(10, 2) NOT NULL CHECK (price > 0),
    stock_quantity INTEGER NOT NULL CHECK (stock_quantity >= 0)
)@@

CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
)@@

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_role
        FOREIGN KEY (role_id) REFERENCES roles(id)
        ON UPDATE CASCADE ON DELETE RESTRICT
)@@

CREATE TABLE IF NOT EXISTS product_variants (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    size VARCHAR(50),
    modifier VARCHAR(100),
    distress VARCHAR(100),
    stains VARCHAR(100),
    CONSTRAINT fk_product_variants_product
        FOREIGN KEY (product_id) REFERENCES products(id)
        ON UPDATE CASCADE ON DELETE RESTRICT
)@@

CREATE TABLE IF NOT EXISTS warehouses (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
)@@

CREATE TABLE IF NOT EXISTS stock (
    id BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_stock_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants(id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uq_stock_warehouse_variant UNIQUE (warehouse_id, variant_id)
)@@

CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(50) NOT NULL UNIQUE,
    recipient_name VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    delivery_address VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    warehouse_id BIGINT,
    stock_deducted BOOLEAN NOT NULL DEFAULT FALSE
)@@
CREATE TABLE IF NOT EXISTS order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    price DECIMAL(10, 2) NOT NULL CHECK (price >= 0),
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_order_items_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants(id)
        ON UPDATE CASCADE ON DELETE RESTRICT
)@@

CREATE OR REPLACE FUNCTION calculate_order_total(p_order_id BIGINT)
RETURNS NUMERIC(12, 2)
LANGUAGE SQL
STABLE
AS $$
    SELECT COALESCE(SUM(oi.quantity * oi.price), 0)::NUMERIC(12, 2)
    FROM order_items oi
    WHERE oi.order_id = p_order_id;
$$@@

ALTER TABLE orders ADD COLUMN IF NOT EXISTS warehouse_id BIGINT@@
ALTER TABLE orders ADD COLUMN IF NOT EXISTS stock_deducted BOOLEAN NOT NULL DEFAULT FALSE@@

CREATE TABLE IF NOT EXISTS inventory_movements (
    id BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    order_id BIGINT,
    movement_type VARCHAR(30) NOT NULL
        CHECK (movement_type IN ('OPENING', 'RECONCILIATION', 'RECEIPT', 'ISSUE', 'ORDER_ISSUE', 'ORDER_RETURN')),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    quantity_before INTEGER NOT NULL CHECK (quantity_before >= 0),
    quantity_after INTEGER NOT NULL CHECK (quantity_after >= 0),
    note VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inventory_movements_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_movements_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants(id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_movements_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
        ON UPDATE CASCADE ON DELETE RESTRICT
)@@

CREATE UNIQUE INDEX IF NOT EXISTS uq_inventory_opening_per_stock
    ON inventory_movements (warehouse_id, variant_id)
    WHERE movement_type = 'OPENING'@@

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    old_price DECIMAL(10, 2) NOT NULL,
    new_price DECIMAL(10, 2) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
)@@

CREATE TABLE IF NOT EXISTS product_creation_log (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    stock_quantity INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
)@@

CREATE OR REPLACE FUNCTION log_price_change()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.price IS DISTINCT FROM NEW.price THEN
        INSERT INTO audit_log (product_id, product_name, old_price, new_price)
        VALUES (OLD.id, NEW.name, OLD.price, NEW.price);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql@@

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'product_price_change'
          AND tgrelid = 'products'::regclass
          AND NOT tgisinternal
    ) THEN
        CREATE TRIGGER product_price_change
        AFTER UPDATE ON products
        FOR EACH ROW
        EXECUTE FUNCTION log_price_change();
    END IF;
END;
$$@@

CREATE OR REPLACE FUNCTION log_product_creation()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO product_creation_log (product_id, product_name, price, stock_quantity)
    VALUES (NEW.id, NEW.name, NEW.price, NEW.stock_quantity);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql@@

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'product_creation'
          AND tgrelid = 'products'::regclass
          AND NOT tgisinternal
    ) THEN
        CREATE TRIGGER product_creation
        AFTER INSERT ON products
        FOR EACH ROW
        EXECUTE FUNCTION log_product_creation();
    END IF;
END;
$$@@

INSERT INTO products (name, price, stock_quantity)
SELECT seed.name, seed.price, seed.stock_quantity
FROM (VALUES
    ('Худи Reflection', 6500.00, 12),
    ('Футболка Basic', 3200.00, 25),
    ('Футболка Oversize', 3900.00, 4),
    ('Сумка Daily', 4800.00, 8),
    ('Ремень Classic', 2700.00, 0),
    ('Лонгслив Essential', 4200.00, 3)
) AS seed(name, price, stock_quantity)
WHERE NOT EXISTS (
    SELECT 1 FROM products existing WHERE existing.name = seed.name
)@@

INSERT INTO roles (name)
SELECT seed.name
FROM (VALUES ('ADMIN'), ('MANAGER')) AS seed(name)
WHERE NOT EXISTS (
    SELECT 1 FROM roles existing WHERE existing.name = seed.name
)@@

INSERT INTO users (username, password_hash, role_id, created_at)
SELECT seed.username, seed.password_hash, r.id, CURRENT_TIMESTAMP
FROM (VALUES
    ('admin', '$2a$10$QWj2m8aABt4iB4vM0XJYIOnh6sG8xRP2G7Qq5q2Qqg6l5yU8aV5S', 'ADMIN'),
    ('manager', '$2a$10$2zjq3NfIVXrD8s6zE3i8P.Cqf0gPZ2wz9PtK7ZVxVQ8kn0V1fN7jG', 'MANAGER')
) AS seed(username, password_hash, role_name)
JOIN roles r ON r.name = seed.role_name
WHERE NOT EXISTS (
    SELECT 1 FROM users existing WHERE existing.username = seed.username
)@@

INSERT INTO warehouses (name, address)
SELECT seed.name, seed.address
FROM (VALUES
    ('Основной склад', 'ул. Складская, 12, Москва')
) AS seed(name, address)
WHERE NOT EXISTS (
    SELECT 1 FROM warehouses existing WHERE existing.name = seed.name
)@@

UPDATE orders
SET warehouse_id = (SELECT id FROM warehouses ORDER BY id LIMIT 1)
WHERE warehouse_id IS NULL@@

ALTER TABLE orders ALTER COLUMN warehouse_id SET NOT NULL@@

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_orders_warehouse'
          AND conrelid = 'orders'::regclass
    ) THEN
        ALTER TABLE orders
            ADD CONSTRAINT fk_orders_warehouse
            FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
            ON UPDATE CASCADE ON DELETE RESTRICT;
    END IF;
END;
$$@@

INSERT INTO product_variants (product_id, size, modifier, distress, stains)
SELECT p.id, seed.size, seed.modifier, seed.distress, seed.stains
FROM (VALUES
    ('Худи Reflection', 'M', 'heavyweight', 'minimal', 'none'),
    ('Худи Reflection', 'L', 'oversize', 'worn', 'light'),
    ('Футболка Basic', 'S', 'cotton', 'minimal', 'none'),
    ('Футболка Oversize', 'L', 'boxy', 'soft', 'none'),
    ('Сумка Daily', 'one size', 'daily', 'minimal', 'none')
) AS seed(product_name, size, modifier, distress, stains)
JOIN LATERAL (
    SELECT id
    FROM products
    WHERE name = seed.product_name
    ORDER BY id
    LIMIT 1
) p ON TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM product_variants existing
    WHERE existing.product_id = p.id
      AND existing.size = seed.size
      AND COALESCE(existing.modifier, '') = COALESCE(seed.modifier, '')
)@@

INSERT INTO stock (warehouse_id, variant_id, quantity, updated_at)
SELECT w.id, pv.id, seed.quantity, CURRENT_TIMESTAMP
FROM (VALUES
    ('Худи Reflection', 'M', 6),
    ('Худи Reflection', 'L', 3),
    ('Футболка Basic', 'S', 12),
    ('Футболка Oversize', 'L', 2),
    ('Сумка Daily', 'one size', 4)
) AS seed(product_name, size, quantity)
JOIN LATERAL (
    SELECT id
    FROM products
    WHERE name = seed.product_name
    ORDER BY id
    LIMIT 1
) p ON TRUE
JOIN product_variants pv ON pv.product_id = p.id AND pv.size = seed.size
JOIN LATERAL (
    SELECT id
    FROM warehouses
    WHERE name = 'Основной склад'
    ORDER BY id
    LIMIT 1
) w ON TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM stock existing
    WHERE existing.warehouse_id = w.id AND existing.variant_id = pv.id
)@@

INSERT INTO orders (
    order_number, recipient_name, phone, delivery_address, status, created_at, warehouse_id
)
SELECT seed.order_number, seed.recipient_name, seed.phone, seed.delivery_address,
       seed.status, CURRENT_TIMESTAMP, w.id
FROM (VALUES
    ('ORD-1001', 'Анна Иванова', '+7 900 111-22-33', 'Москва, ул. Тверская, 12', 'NEW'),
    ('ORD-1002', 'Петр Сидоров', '+7 900 444-55-66', 'Москва, ул. Лесная, 5', 'PROCESSING')
) AS seed(order_number, recipient_name, phone, delivery_address, status)
CROSS JOIN LATERAL (
    SELECT id FROM warehouses ORDER BY id LIMIT 1
) w
WHERE NOT EXISTS (
    SELECT 1 FROM orders existing WHERE existing.order_number = seed.order_number
)@@

INSERT INTO order_items (order_id, variant_id, quantity, price)
SELECT o.id, pv.id, seed.quantity, seed.price
FROM (VALUES
    ('ORD-1001', 'Худи Reflection', 'M', 1, 6500.00),
    ('ORD-1001', 'Футболка Basic', 'S', 2, 3200.00),
    ('ORD-1002', 'Футболка Oversize', 'L', 1, 3900.00)
) AS seed(order_number, product_name, size, quantity, price)
JOIN orders o ON o.order_number = seed.order_number
JOIN LATERAL (
    SELECT id
    FROM products
    WHERE name = seed.product_name
    ORDER BY id
    LIMIT 1
) p ON TRUE
JOIN product_variants pv ON pv.product_id = p.id AND pv.size = seed.size
WHERE NOT EXISTS (
    SELECT 1 FROM order_items existing
    WHERE existing.order_id = o.id
      AND existing.variant_id = pv.id
)@@

UPDATE orders
SET status = 'NEW'
WHERE status = 'PROCESSING'
  AND stock_deducted = FALSE@@

INSERT INTO product_variants (product_id, size, modifier, distress, stains)
SELECT p.id, 'ONE SIZE', 'base', 'none', 'none'
FROM products p
WHERE NOT EXISTS (
    SELECT 1 FROM product_variants existing WHERE existing.product_id = p.id
)@@

INSERT INTO stock (warehouse_id, variant_id, quantity, updated_at)
SELECT w.id, v.id, 0, CURRENT_TIMESTAMP
FROM product_variants v
CROSS JOIN LATERAL (
    SELECT id FROM warehouses ORDER BY id LIMIT 1
) w
WHERE NOT EXISTS (
    SELECT 1 FROM stock existing
    WHERE existing.warehouse_id = w.id AND existing.variant_id = v.id
)@@

DO $$
DECLARE
    product_row RECORD;
    stock_row RECORD;
    total_quantity INTEGER;
    quantity_difference INTEGER;
    adjustment INTEGER;
    remaining_adjustment INTEGER;
    movement_warehouse_id BIGINT;
BEGIN
    SELECT id INTO movement_warehouse_id FROM warehouses ORDER BY id LIMIT 1;

    FOR product_row IN
        SELECT id, stock_quantity
        FROM products
        ORDER BY id
    LOOP
        SELECT COALESCE(SUM(s.quantity), 0)::INTEGER
        INTO total_quantity
        FROM stock s
        JOIN product_variants v ON v.id = s.variant_id
        WHERE v.product_id = product_row.id;

        quantity_difference := product_row.stock_quantity - total_quantity;

        IF quantity_difference > 0 THEN
            SELECT s.id, s.quantity, v.id AS variant_id
            INTO stock_row
            FROM stock s
            JOIN product_variants v ON v.id = s.variant_id
            WHERE v.product_id = product_row.id
              AND s.warehouse_id = movement_warehouse_id
            ORDER BY v.id
            LIMIT 1
            FOR UPDATE OF s;

            UPDATE stock
            SET quantity = stock_row.quantity + quantity_difference,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = stock_row.id;

            INSERT INTO inventory_movements (
                warehouse_id, variant_id, movement_type, quantity,
                quantity_before, quantity_after, note
            ) VALUES (
                movement_warehouse_id, stock_row.variant_id, 'RECONCILIATION',
                quantity_difference, stock_row.quantity,
                stock_row.quantity + quantity_difference,
                'Сверка начального остатка с legacy products.stock_quantity'
            );
        ELSIF quantity_difference < 0 THEN
            remaining_adjustment := -quantity_difference;

            FOR stock_row IN
                SELECT s.id, s.warehouse_id, s.variant_id, s.quantity
                FROM stock s
                JOIN product_variants v ON v.id = s.variant_id
                WHERE v.product_id = product_row.id
                  AND s.quantity > 0
                ORDER BY v.id, s.id
                FOR UPDATE OF s
            LOOP
                EXIT WHEN remaining_adjustment = 0;
                adjustment := LEAST(stock_row.quantity, remaining_adjustment);

                UPDATE stock
                SET quantity = stock_row.quantity - adjustment,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = stock_row.id;

                INSERT INTO inventory_movements (
                    warehouse_id, variant_id, movement_type, quantity,
                    quantity_before, quantity_after, note
                ) VALUES (
                    stock_row.warehouse_id, stock_row.variant_id, 'RECONCILIATION',
                    adjustment, stock_row.quantity, stock_row.quantity - adjustment,
                    'Сверка начального остатка с legacy products.stock_quantity'
                );

                remaining_adjustment := remaining_adjustment - adjustment;
            END LOOP;
        END IF;

        UPDATE products
        SET stock_quantity = (
            SELECT COALESCE(SUM(s.quantity), 0)::INTEGER
            FROM stock s
            JOIN product_variants v ON v.id = s.variant_id
            WHERE v.product_id = product_row.id
        )
        WHERE id = product_row.id;
    END LOOP;
END;
$$@@

INSERT INTO inventory_movements (
    warehouse_id, variant_id, movement_type, quantity,
    quantity_before, quantity_after, note
)
SELECT s.warehouse_id, s.variant_id, 'OPENING', s.quantity,
       0, s.quantity, 'Начальный остаток после переноса складской модели'
FROM stock s
WHERE s.quantity > 0
  AND NOT EXISTS (
      SELECT 1
      FROM inventory_movements existing
      WHERE existing.warehouse_id = s.warehouse_id
        AND existing.variant_id = s.variant_id
        AND existing.movement_type = 'OPENING'
  )@@
