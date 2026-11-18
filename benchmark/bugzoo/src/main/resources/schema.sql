-- Members borrow from a small shop: members, products, orders.
CREATE TABLE IF NOT EXISTS member (
    id            BIGSERIAL PRIMARY KEY,
    member_number INT NOT NULL UNIQUE,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150),
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS product (
    id    BIGSERIAL PRIMARY KEY,
    name  VARCHAR(100) NOT NULL,
    stock INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS purchase_order (
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT NOT NULL REFERENCES member (id),
    product    VARCHAR(100) NOT NULL,
    quantity   INT NOT NULL,
    order_date DATE NOT NULL
);
