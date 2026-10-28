CREATE TABLE activation_request (
    id            UUID         NOT NULL,
    external_id   VARCHAR(64)  NOT NULL,
    customer_id   VARCHAR(64)  NOT NULL,
    product_code  VARCHAR(64)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    version       BIGINT       NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_activation_request PRIMARY KEY (id),
    -- Fonte da verdade contra duplicidade: duas inserções com o mesmo external_id
    -- nunca coexistem, mesmo em transações concorrentes.
    CONSTRAINT uk_activation_request_external_id UNIQUE (external_id)
);
