-- BillSync initial schema.
--
-- Two deliberate choices worth knowing:
--   * The groups table is called expense_groups because GROUP is a reserved
--     word in PostgreSQL and naming it "groups" forces quoting everywhere.
--   * Every money column is NUMERIC(12,2), never a floating-point type.
--     Floating point cannot represent 0.10 exactly and balances would drift.

CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(255) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE expense_groups (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE group_members (
    id          BIGSERIAL PRIMARY KEY,
    group_id    BIGINT NOT NULL REFERENCES expense_groups (id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users (id),
    UNIQUE (group_id, user_id)
);

CREATE TABLE expenses (
    id               BIGSERIAL PRIMARY KEY,
    group_id         BIGINT        NOT NULL REFERENCES expense_groups (id) ON DELETE CASCADE,
    paid_by_user_id  BIGINT        NOT NULL REFERENCES users (id),
    amount           NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    description      VARCHAR(255)  NOT NULL,
    split_type       VARCHAR(20)   NOT NULL CHECK (split_type IN ('EQUAL', 'EXACT', 'PERCENTAGE')),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE expense_shares (
    id           BIGSERIAL PRIMARY KEY,
    expense_id   BIGINT        NOT NULL REFERENCES expenses (id) ON DELETE CASCADE,
    user_id      BIGINT        NOT NULL REFERENCES users (id),
    amount_owed  NUMERIC(12,2) NOT NULL,
    UNIQUE (expense_id, user_id)
);

CREATE TABLE settlements (
    id            BIGSERIAL PRIMARY KEY,
    group_id      BIGINT        NOT NULL REFERENCES expense_groups (id) ON DELETE CASCADE,
    from_user_id  BIGINT        NOT NULL REFERENCES users (id),
    to_user_id    BIGINT        NOT NULL REFERENCES users (id),
    amount        NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    settled_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
