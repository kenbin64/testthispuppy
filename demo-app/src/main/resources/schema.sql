-- Two related tables: a customer places purchase orders.
-- The foreign key and the check constraint make cleanup and dummy data prove they respect the rules.
CREATE TABLE IF NOT EXISTS customer (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(150) NOT NULL,
    phone      VARCHAR(30),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS purchase_order (
    id          BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customer (id),
    product     VARCHAR(100) NOT NULL,
    quantity    INT NOT NULL CHECK (quantity > 0),
    status      VARCHAR(20) NOT NULL DEFAULT 'NEW',
    order_date  DATE NOT NULL
);
