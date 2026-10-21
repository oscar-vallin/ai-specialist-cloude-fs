-- V1: initial schema, derived from docs/data-model.md.
-- Conventions: UUID primary key on every table, created_at/updated_at on every table,
-- money is NUMERIC(12,2), enums are VARCHAR + CHECK, FKs default to RESTRICT.
-- No seed data except the three reference roles (see the seed-data skill for sample data).

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------------------
-- Member
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email              VARCHAR(255) NOT NULL,
    password_hash      VARCHAR(255) NOT NULL,
    first_name         VARCHAR(100) NOT NULL,
    last_name          VARCHAR(100) NOT NULL,
    phone_country_code VARCHAR(8),
    phone_number       VARCHAR(32),
    status             VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

CREATE TABLE roles (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_roles_name UNIQUE (name),
    CONSTRAINT ck_roles_name CHECK (name IN ('GUEST', 'CUSTOMER', 'ADMIN'))
);

-- Reference data. GUEST exists so catalogs can be entitled to guests; it is never assigned to users.
INSERT INTO roles (name) VALUES ('GUEST'), ('CUSTOMER'), ('ADMIN');

CREATE TABLE user_roles (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id    UUID        NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_roles UNIQUE (user_id, role_id)
);

CREATE TABLE revoked_tokens (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    jti        VARCHAR(64)  NOT NULL,
    user_id    UUID         NOT NULL REFERENCES users (id),
    expires_at TIMESTAMPTZ  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_revoked_tokens_jti UNIQUE (jti)
);
CREATE INDEX ix_revoked_tokens_expires_at ON revoked_tokens (expires_at);

CREATE TABLE addresses (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL REFERENCES users (id),
    label              VARCHAR(50),
    first_name         VARCHAR(100) NOT NULL,
    last_name          VARCHAR(100) NOT NULL,
    line1              VARCHAR(255) NOT NULL,
    line2              VARCHAR(255),
    city               VARCHAR(100) NOT NULL,
    state              VARCHAR(100) NOT NULL,
    postal_code        VARCHAR(20)  NOT NULL,
    country            CHAR(2)      NOT NULL,
    email              VARCHAR(255),
    phone_country_code VARCHAR(8),
    phone_number       VARCHAR(32),
    is_default         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_addresses_user_id ON addresses (user_id);
CREATE UNIQUE INDEX uq_addresses_one_default_per_user ON addresses (user_id) WHERE is_default;

-- ---------------------------------------------------------------------------
-- Store
-- ---------------------------------------------------------------------------

CREATE TABLE stores (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(150) NOT NULL,
    status     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_stores_name UNIQUE (name),
    CONSTRAINT ck_stores_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE store_policies (
    id                           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id                     UUID          NOT NULL REFERENCES stores (id),
    cancellation_window_hours    INTEGER       NOT NULL DEFAULT 48,
    return_window_days           INTEGER       NOT NULL DEFAULT 7,
    free_shipping_threshold      NUMERIC(12,2),
    tax_rate                     NUMERIC(5,4)  NOT NULL,
    gift_points_per_currency_unit INTEGER      NOT NULL DEFAULT 100,
    max_gift_points_percent      INTEGER       NOT NULL DEFAULT 100,
    created_at                   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_store_policies_store UNIQUE (store_id),
    CONSTRAINT ck_store_policies_cancel CHECK (cancellation_window_hours > 0),
    CONSTRAINT ck_store_policies_return CHECK (return_window_days > 0),
    CONSTRAINT ck_store_policies_free_ship CHECK (free_shipping_threshold IS NULL OR free_shipping_threshold >= 0),
    CONSTRAINT ck_store_policies_tax CHECK (tax_rate BETWEEN 0 AND 1),
    CONSTRAINT ck_store_policies_points_rate CHECK (gift_points_per_currency_unit > 0),
    CONSTRAINT ck_store_policies_points_pct CHECK (max_gift_points_percent BETWEEN 0 AND 100)
);

CREATE TABLE catalogs (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id   UUID         NOT NULL REFERENCES stores (id),
    name       VARCHAR(150) NOT NULL,
    status     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_catalogs_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE INDEX ix_catalogs_store_status ON catalogs (store_id, status);

-- ---------------------------------------------------------------------------
-- Catalog
-- ---------------------------------------------------------------------------

CREATE TABLE categories (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id     UUID         REFERENCES categories (id),
    name          VARCHAR(100) NOT NULL,
    slug          VARCHAR(120) NOT NULL,
    display_order INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_categories_slug UNIQUE (slug),
    CONSTRAINT ck_categories_not_self_parent CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE INDEX ix_categories_parent_order ON categories (parent_id, display_order);

CREATE TABLE brands (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_brands_name UNIQUE (name)
);

CREATE TABLE authors (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(200) NOT NULL,
    bio        TEXT,
    photo_url  VARCHAR(500),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_authors_name_trgm ON authors USING gin (name gin_trgm_ops);

CREATE TABLE products (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    isbn             VARCHAR(17)   NOT NULL,
    title            VARCHAR(300)  NOT NULL,
    tagline          VARCHAR(500),
    description      TEXT,
    format           VARCHAR(16)   NOT NULL,
    language         CHAR(2)       NOT NULL,
    price            NUMERIC(12,2) NOT NULL,
    brand_id         UUID          NOT NULL REFERENCES brands (id),
    cover_front_url  VARCHAR(500)  NOT NULL,
    cover_back_url   VARCHAR(500),
    weight_grams     INTEGER,
    publication_date DATE,
    status           VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_products_isbn UNIQUE (isbn),
    CONSTRAINT ck_products_format CHECK (format IN ('PAPERBACK', 'HARDCOVER', 'EBOOK')),
    CONSTRAINT ck_products_price CHECK (price >= 0),
    CONSTRAINT ck_products_weight CHECK (weight_grams IS NULL OR weight_grams > 0),
    CONSTRAINT ck_products_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);
CREATE INDEX ix_products_status_brand ON products (status, brand_id);
CREATE INDEX ix_products_status_price ON products (status, price);
CREATE INDEX ix_products_status_language_format ON products (status, language, format);
CREATE INDEX ix_products_status_publication ON products (status, publication_date DESC);
CREATE INDEX ix_products_title_trgm ON products USING gin (title gin_trgm_ops);
CREATE INDEX ix_products_tagline_trgm ON products USING gin (tagline gin_trgm_ops);

CREATE TABLE catalog_products (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    catalog_id UUID        NOT NULL REFERENCES catalogs (id) ON DELETE CASCADE,
    product_id UUID        NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_catalog_products UNIQUE (catalog_id, product_id)
);
CREATE INDEX ix_catalog_products_product ON catalog_products (product_id);

CREATE TABLE catalog_entitlements (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    catalog_id UUID        NOT NULL REFERENCES catalogs (id) ON DELETE CASCADE,
    role_id    UUID        NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_catalog_entitlements UNIQUE (catalog_id, role_id)
);

CREATE TABLE product_authors (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id   UUID        NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    author_id    UUID        NOT NULL REFERENCES authors (id) ON DELETE CASCADE,
    author_order INTEGER     NOT NULL DEFAULT 1,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_product_authors UNIQUE (product_id, author_id)
);
CREATE INDEX ix_product_authors_author ON product_authors (author_id);

CREATE TABLE product_categories (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  UUID        NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    category_id UUID        NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    is_primary  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_product_categories UNIQUE (product_id, category_id)
);
CREATE INDEX ix_product_categories_category_product ON product_categories (category_id, product_id);
CREATE UNIQUE INDEX uq_product_categories_one_primary ON product_categories (product_id) WHERE is_primary;

CREATE TABLE inventory (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id         UUID        NOT NULL REFERENCES products (id),
    quantity_available INTEGER     NOT NULL DEFAULT 0,
    quantity_reserved  INTEGER     NOT NULL DEFAULT 0,
    version            BIGINT      NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_inventory_product UNIQUE (product_id),
    CONSTRAINT ck_inventory_available CHECK (quantity_available >= 0),
    CONSTRAINT ck_inventory_reserved CHECK (quantity_reserved >= 0),
    CONSTRAINT ck_inventory_reserved_le_available CHECK (quantity_reserved <= quantity_available)
);

CREATE TABLE related_products (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id         UUID        NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    related_product_id UUID        NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    relation_type      VARCHAR(16) NOT NULL,
    position           INTEGER     NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_related_products UNIQUE (product_id, related_product_id, relation_type),
    CONSTRAINT ck_related_products_type CHECK (relation_type IN ('RELATED', 'UP_SELL', 'CROSS_SELL')),
    CONSTRAINT ck_related_products_not_self CHECK (product_id <> related_product_id)
);
CREATE INDEX ix_related_products_lookup ON related_products (product_id, relation_type, position);

CREATE TABLE reviews (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID         NOT NULL REFERENCES products (id),
    user_id    UUID         NOT NULL REFERENCES users (id),
    rating     SMALLINT     NOT NULL,
    comment    VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_reviews_product_user UNIQUE (product_id, user_id),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_reviews_comment CHECK (char_length(comment) <= 100)
);
CREATE INDEX ix_reviews_product ON reviews (product_id);

CREATE TABLE wishlist_items (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    product_id UUID        NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_wishlist_items UNIQUE (user_id, product_id)
);

CREATE TABLE followed_authors (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    author_id  UUID        NOT NULL REFERENCES authors (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_followed_authors UNIQUE (user_id, author_id)
);

-- ---------------------------------------------------------------------------
-- Cart and Order
-- ---------------------------------------------------------------------------

CREATE TABLE coupons (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id         UUID          REFERENCES stores (id),
    code             VARCHAR(50)   NOT NULL,
    discount_type    VARCHAR(16)   NOT NULL,
    discount_value   NUMERIC(12,2) NOT NULL,
    min_order_amount NUMERIC(12,2),
    max_redemptions  INTEGER,
    valid_from       TIMESTAMPTZ,
    valid_until      TIMESTAMPTZ,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_coupons_type CHECK (discount_type IN ('PERCENT', 'FIXED')),
    CONSTRAINT ck_coupons_value CHECK (discount_value > 0),
    CONSTRAINT ck_coupons_percent CHECK (discount_type <> 'PERCENT' OR discount_value <= 100),
    CONSTRAINT ck_coupons_min_order CHECK (min_order_amount IS NULL OR min_order_amount >= 0),
    CONSTRAINT ck_coupons_max_redemptions CHECK (max_redemptions IS NULL OR max_redemptions > 0),
    CONSTRAINT ck_coupons_validity CHECK (valid_from IS NULL OR valid_until IS NULL OR valid_from < valid_until)
);
CREATE UNIQUE INDEX uq_coupons_code_lower ON coupons (lower(code));
CREATE INDEX ix_coupons_store ON coupons (store_id);

CREATE TABLE carts (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id            UUID        NOT NULL REFERENCES stores (id),
    user_id             UUID        REFERENCES users (id),
    guest_token         VARCHAR(64),
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    merged_into_cart_id UUID        REFERENCES carts (id),
    coupon_id           UUID        REFERENCES coupons (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_carts_guest_token UNIQUE (guest_token),
    CONSTRAINT ck_carts_status CHECK (status IN ('ACTIVE', 'MERGED', 'CONVERTED', 'ABANDONED')),
    CONSTRAINT ck_carts_owner CHECK (user_id IS NOT NULL OR guest_token IS NOT NULL),
    CONSTRAINT ck_carts_merged CHECK (status <> 'MERGED' OR merged_into_cart_id IS NOT NULL),
    CONSTRAINT ck_carts_not_self_merged CHECK (merged_into_cart_id IS NULL OR merged_into_cart_id <> id)
);
CREATE UNIQUE INDEX uq_carts_one_active_per_user_store ON carts (user_id, store_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_carts_status_updated ON carts (status, updated_at);

CREATE TABLE cart_items (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id    UUID        NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    product_id UUID        NOT NULL REFERENCES products (id),
    quantity   INTEGER     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cart_items UNIQUE (cart_id, product_id),
    CONSTRAINT ck_cart_items_quantity CHECK (quantity > 0)
);
CREATE INDEX ix_cart_items_product ON cart_items (product_id);

CREATE TABLE orders (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number        VARCHAR(32)   NOT NULL,
    user_id             UUID          NOT NULL REFERENCES users (id),
    store_id            UUID          NOT NULL REFERENCES stores (id),
    cart_id             UUID          REFERENCES carts (id),
    status              VARCHAR(24)   NOT NULL,
    currency            CHAR(3)       NOT NULL DEFAULT 'USD',
    subtotal            NUMERIC(12,2) NOT NULL,
    tax_total           NUMERIC(12,2) NOT NULL DEFAULT 0,
    shipping_total      NUMERIC(12,2) NOT NULL DEFAULT 0,
    coupon_discount     NUMERIC(12,2) NOT NULL DEFAULT 0,
    total               NUMERIC(12,2) NOT NULL,
    coupon_id           UUID          REFERENCES coupons (id),
    ship_first_name     VARCHAR(100)  NOT NULL,
    ship_last_name      VARCHAR(100)  NOT NULL,
    ship_line1          VARCHAR(255)  NOT NULL,
    ship_line2          VARCHAR(255),
    ship_city           VARCHAR(100)  NOT NULL,
    ship_state          VARCHAR(100)  NOT NULL,
    ship_postal_code    VARCHAR(20)   NOT NULL,
    ship_country        CHAR(2)       NOT NULL,
    ship_email          VARCHAR(255),
    ship_phone          VARCHAR(40),
    placed_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    payment_expires_at  TIMESTAMPTZ,
    confirmed_at        TIMESTAMPTZ,
    cancel_deadline_at  TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    cancellation_reason VARCHAR(500),
    delivered_at        TIMESTAMPTZ,
    return_deadline_at  TIMESTAMPTZ,
    idempotency_key     VARCHAR(100)  NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_orders_order_number UNIQUE (order_number),
    CONSTRAINT uq_orders_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING_PAYMENT', 'PAYMENT_FAILED', 'CONFIRMED', 'SHIPPED',
        'DELIVERED', 'CANCELLED', 'RETURN_REQUESTED', 'RETURNED')),
    CONSTRAINT ck_orders_amounts CHECK (subtotal >= 0 AND tax_total >= 0 AND shipping_total >= 0
        AND coupon_discount >= 0 AND total >= 0),
    CONSTRAINT ck_orders_total CHECK (total = subtotal + tax_total + shipping_total - coupon_discount)
);
CREATE INDEX ix_orders_user_placed ON orders (user_id, placed_at DESC);
CREATE INDEX ix_orders_store_placed ON orders (store_id, placed_at DESC);
CREATE INDEX ix_orders_cancel_deadline ON orders (cancel_deadline_at) WHERE status = 'CONFIRMED';
CREATE INDEX ix_orders_payment_expiry ON orders (payment_expires_at) WHERE status = 'PENDING_PAYMENT';

CREATE TABLE order_items (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                UUID          NOT NULL REFERENCES orders (id),
    product_id              UUID          NOT NULL REFERENCES products (id),
    title                   VARCHAR(300)  NOT NULL,
    isbn                    VARCHAR(17)   NOT NULL,
    unit_price              NUMERIC(12,2) NOT NULL,
    quantity                INTEGER       NOT NULL,
    line_total              NUMERIC(12,2) NOT NULL,
    tax_amount              NUMERIC(12,2) NOT NULL DEFAULT 0,
    estimated_delivery_date DATE,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_order_items_price CHECK (unit_price >= 0),
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_line_total CHECK (line_total = unit_price * quantity),
    CONSTRAINT ck_order_items_tax CHECK (tax_amount >= 0)
);
CREATE INDEX ix_order_items_order ON order_items (order_id);
CREATE INDEX ix_order_items_product ON order_items (product_id);

CREATE TABLE order_status_history (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID        NOT NULL REFERENCES orders (id),
    from_status VARCHAR(24),
    to_status   VARCHAR(24) NOT NULL,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    changed_by  UUID        REFERENCES users (id),
    note        VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_order_status_history_order ON order_status_history (order_id, changed_at);

CREATE TABLE order_returns (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID         NOT NULL REFERENCES orders (id),
    status       VARCHAR(16)  NOT NULL DEFAULT 'REQUESTED',
    reason       VARCHAR(500) NOT NULL,
    requested_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_order_returns_status CHECK (status IN ('REQUESTED', 'APPROVED', 'RECEIVED', 'REFUNDED', 'REJECTED'))
);
CREATE UNIQUE INDEX uq_order_returns_one_open ON order_returns (order_id) WHERE status <> 'REJECTED';

CREATE TABLE coupon_redemptions (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    coupon_id   UUID        NOT NULL REFERENCES coupons (id),
    order_id    UUID        NOT NULL REFERENCES orders (id),
    user_id     UUID        NOT NULL REFERENCES users (id),
    redeemed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_coupon_redemptions_order UNIQUE (order_id)
);
CREATE INDEX ix_coupon_redemptions_coupon_user ON coupon_redemptions (coupon_id, user_id);

-- ---------------------------------------------------------------------------
-- Payment
-- ---------------------------------------------------------------------------

CREATE TABLE payments (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id          UUID          NOT NULL REFERENCES orders (id),
    method            VARCHAR(16)   NOT NULL,
    status            VARCHAR(24)   NOT NULL DEFAULT 'PENDING',
    amount            NUMERIC(12,2) NOT NULL,
    points_used       INTEGER,
    card_brand        VARCHAR(32),
    card_last4        CHAR(4),
    gateway_reference VARCHAR(100),
    failure_reason    VARCHAR(500),
    idempotency_key   VARCHAR(100)  NOT NULL,
    processed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_payments_order_method_key UNIQUE (order_id, method, idempotency_key),
    CONSTRAINT ck_payments_method CHECK (method IN ('CREDIT_CARD', 'DEBIT_CARD', 'WALLET', 'GIFT_POINTS')),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'AUTHORIZED', 'CAPTURED', 'FAILED', 'REFUNDED',
        'PARTIALLY_REFUNDED')),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_points CHECK ((method = 'GIFT_POINTS' AND points_used IS NOT NULL AND points_used > 0)
        OR (method <> 'GIFT_POINTS' AND points_used IS NULL))
);
CREATE INDEX ix_payments_order ON payments (order_id);

CREATE TABLE refunds (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id  UUID          NOT NULL REFERENCES payments (id),
    order_id    UUID          NOT NULL REFERENCES orders (id),
    return_id   UUID          REFERENCES order_returns (id),
    amount      NUMERIC(12,2) NOT NULL,
    status      VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    reason      VARCHAR(500)  NOT NULL,
    refunded_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_refunds_amount CHECK (amount > 0),
    CONSTRAINT ck_refunds_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
);
CREATE INDEX ix_refunds_payment ON refunds (payment_id);
CREATE INDEX ix_refunds_order ON refunds (order_id);

CREATE TABLE wallets (
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID          NOT NULL REFERENCES users (id),
    balance    NUMERIC(12,2) NOT NULL DEFAULT 0,
    version    BIGINT        NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_wallets_user UNIQUE (user_id),
    CONSTRAINT ck_wallets_balance CHECK (balance >= 0)
);

CREATE TABLE wallet_transactions (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id     UUID          NOT NULL REFERENCES wallets (id),
    type          VARCHAR(8)    NOT NULL,
    amount        NUMERIC(12,2) NOT NULL,
    reason        VARCHAR(16)   NOT NULL,
    order_id      UUID          REFERENCES orders (id),
    payment_id    UUID          REFERENCES payments (id),
    refund_id     UUID          REFERENCES refunds (id),
    balance_after NUMERIC(12,2) NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_wallet_transactions_type CHECK (type IN ('CREDIT', 'DEBIT')),
    CONSTRAINT ck_wallet_transactions_amount CHECK (amount > 0),
    CONSTRAINT ck_wallet_transactions_reason CHECK (reason IN ('PAYMENT', 'REFUND', 'TOP_UP', 'ADJUSTMENT')),
    CONSTRAINT ck_wallet_transactions_balance CHECK (balance_after >= 0)
);
CREATE INDEX ix_wallet_transactions_wallet_created ON wallet_transactions (wallet_id, created_at DESC);
CREATE UNIQUE INDEX uq_wallet_transactions_refund ON wallet_transactions (refund_id) WHERE refund_id IS NOT NULL;
CREATE UNIQUE INDEX uq_wallet_transactions_payment ON wallet_transactions (payment_id) WHERE reason = 'PAYMENT';

CREATE TABLE gift_point_accounts (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id),
    balance    BIGINT      NOT NULL DEFAULT 0,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_gift_point_accounts_user UNIQUE (user_id),
    CONSTRAINT ck_gift_point_accounts_balance CHECK (balance >= 0)
);

CREATE TABLE gift_point_ledger (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL REFERENCES users (id),
    points        BIGINT      NOT NULL,
    type          VARCHAR(16) NOT NULL,
    order_id      UUID        REFERENCES orders (id),
    payment_id    UUID        REFERENCES payments (id),
    refund_id     UUID        REFERENCES refunds (id),
    balance_after BIGINT      NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_gift_point_ledger_type CHECK (type IN ('EARNED', 'REDEEMED', 'REFUNDED', 'ADJUSTMENT')),
    CONSTRAINT ck_gift_point_ledger_points CHECK (points <> 0),
    CONSTRAINT ck_gift_point_ledger_balance CHECK (balance_after >= 0)
);
CREATE INDEX ix_gift_point_ledger_user_created ON gift_point_ledger (user_id, created_at DESC);
CREATE UNIQUE INDEX uq_gift_point_ledger_refund ON gift_point_ledger (refund_id) WHERE refund_id IS NOT NULL;
CREATE UNIQUE INDEX uq_gift_point_ledger_payment ON gift_point_ledger (payment_id) WHERE type = 'REDEEMED';

-- ---------------------------------------------------------------------------
-- Shipping
-- ---------------------------------------------------------------------------

CREATE TABLE shipping_rates (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id           UUID          NOT NULL REFERENCES stores (id),
    country            CHAR(2)       NOT NULL,
    min_weight_grams   INTEGER       NOT NULL,
    max_weight_grams   INTEGER       NOT NULL,
    rate               NUMERIC(12,2) NOT NULL,
    estimated_days_min INTEGER       NOT NULL,
    estimated_days_max INTEGER       NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_shipping_rates_weight CHECK (min_weight_grams >= 0 AND min_weight_grams < max_weight_grams),
    CONSTRAINT ck_shipping_rates_rate CHECK (rate >= 0),
    CONSTRAINT ck_shipping_rates_days CHECK (estimated_days_min >= 0 AND estimated_days_min <= estimated_days_max),
    -- Weight bands of the same store and country must not overlap (upper bound exclusive).
    CONSTRAINT ex_shipping_rates_no_overlap EXCLUDE USING gist (
        store_id WITH =,
        country WITH =,
        int4range(min_weight_grams, max_weight_grams, '[)') WITH &&
    )
);
CREATE INDEX ix_shipping_rates_lookup ON shipping_rates (store_id, country, min_weight_grams);

CREATE TABLE shipments (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                UUID          NOT NULL REFERENCES orders (id),
    type                    VARCHAR(16)   NOT NULL,
    return_id               UUID          REFERENCES order_returns (id),
    status                  VARCHAR(16)   NOT NULL DEFAULT 'CREATED',
    carrier                 VARCHAR(100),
    tracking_number         VARCHAR(100),
    shipping_cost           NUMERIC(12,2) NOT NULL DEFAULT 0,
    estimated_delivery_date DATE,
    shipped_at              TIMESTAMPTZ,
    delivered_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_shipments_type CHECK (type IN ('OUTBOUND', 'RETURN')),
    CONSTRAINT ck_shipments_status CHECK (status IN ('CREATED', 'IN_TRANSIT', 'DELIVERED', 'FAILED', 'RETURNED')),
    CONSTRAINT ck_shipments_return CHECK ((type = 'RETURN') = (return_id IS NOT NULL)),
    CONSTRAINT ck_shipments_cost CHECK (shipping_cost >= 0)
);
CREATE UNIQUE INDEX uq_shipments_one_outbound_per_order ON shipments (order_id) WHERE type = 'OUTBOUND';
CREATE UNIQUE INDEX uq_shipments_one_per_return ON shipments (return_id) WHERE return_id IS NOT NULL;
CREATE INDEX ix_shipments_tracking ON shipments (tracking_number);
