DROP TABLE IF EXISTS product_creation_log CASCADE@@
DROP TABLE IF EXISTS audit_log CASCADE@@
    DROP TABLE IF EXISTS products CASCADE@@

    CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price DECIMAL(10,2) NOT NULL CHECK (price > 0),
    stock_quantity INTEGER NOT NULL CHECK (stock_quantity >= 0)
    )@@

    CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    old_price DECIMAL(10,2) NOT NULL,
    new_price DECIMAL(10,2) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    )@@

    CREATE OR REPLACE FUNCTION log_price_change()
    RETURNS TRIGGER AS $$
BEGIN
    IF OLD.price IS DISTINCT FROM NEW.price THEN
        INSERT INTO audit_log(
            product_id,
            product_name,
            old_price,
            new_price
        )
        VALUES (
            OLD.id,
            NEW.name,
            OLD.price,
            NEW.price
        );
END IF;

RETURN NEW;
END;
$$ LANGUAGE plpgsql@@

CREATE TRIGGER product_price_change
AFTER UPDATE ON products
             FOR EACH ROW
             EXECUTE FUNCTION log_price_change()@@

             INSERT INTO products(name, price, stock_quantity)
      VALUES
             ('Худи Reflection', 6500.00, 12),
             ('Футболка Basic', 3200.00, 25),
             ('Сумка Daily', 4800.00, 8)@@

          CREATE TABLE product_creation_log (
          id BIGSERIAL PRIMARY KEY,
          product_id BIGINT NOT NULL,
          product_name VARCHAR(255) NOT NULL,
          price DECIMAL(10,2) NOT NULL,
          stock_quantity INTEGER NOT NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
          )@@

          CREATE OR REPLACE FUNCTION log_product_creation()
          RETURNS TRIGGER AS $$
BEGIN
INSERT INTO product_creation_log(
    product_id,
    product_name,
    price,
    stock_quantity
)
VALUES (
           NEW.id,
           NEW.name,
           NEW.price,
           NEW.stock_quantity
       );

RETURN NEW;
END;
$$ LANGUAGE plpgsql@@

CREATE TRIGGER product_creation
AFTER INSERT ON products
FOR EACH ROW
EXECUTE FUNCTION log_product_creation()@@