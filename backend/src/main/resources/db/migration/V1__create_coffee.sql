CREATE TABLE coffee (
    id           UUID                     NOT NULL,
    name         VARCHAR(100)             NOT NULL,
    roast_level  VARCHAR(16)              NOT NULL,
    origin       VARCHAR(100)             NOT NULL,
    price        NUMERIC(10, 2)           NOT NULL,
    stock        INTEGER                  NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_coffee PRIMARY KEY (id),
    CONSTRAINT uq_coffee_name UNIQUE (name),
    CONSTRAINT ck_coffee_price_positive CHECK (price > 0),
    CONSTRAINT ck_coffee_stock_non_negative CHECK (stock >= 0)
);

CREATE INDEX ix_coffee_roast_level ON coffee (roast_level);
CREATE INDEX ix_coffee_name ON coffee (name);
