-- V2: order number sequence and request fingerprints for idempotent order placement, payment and refund.
-- A replay with the same Idempotency-Key and the same fingerprint returns the original result;
-- the same key with a different fingerprint is rejected (IDEMPOTENCY_KEY_REUSED).
-- Schema migrations stay below V1000; V1000 and above are reserved for the local seed (db/seed).

CREATE SEQUENCE order_number_seq START WITH 1 INCREMENT BY 1;

ALTER TABLE orders ADD COLUMN request_hash VARCHAR(64);
UPDATE orders SET request_hash = '' WHERE request_hash IS NULL;
ALTER TABLE orders ALTER COLUMN request_hash SET NOT NULL;

ALTER TABLE payments ADD COLUMN request_hash VARCHAR(64);
UPDATE payments SET request_hash = '' WHERE request_hash IS NULL;
ALTER TABLE payments ALTER COLUMN request_hash SET NOT NULL;

ALTER TABLE refunds ADD COLUMN idempotency_key VARCHAR(100);
ALTER TABLE refunds ADD COLUMN request_hash VARCHAR(64);
CREATE UNIQUE INDEX uq_refunds_payment_idempotency ON refunds (payment_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

-- updated_at is maintained by the database on every UPDATE, so no repository has to remember to set it.
CREATE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    t RECORD;
BEGIN
    FOR t IN
        SELECT c.table_name
        FROM information_schema.columns c
        JOIN information_schema.tables tb ON tb.table_name = c.table_name AND tb.table_schema = c.table_schema
        WHERE c.table_schema = 'public' AND c.column_name = 'updated_at'
          AND tb.table_type = 'BASE TABLE' AND c.table_name <> 'flyway_schema_history'
    LOOP
        EXECUTE format('CREATE TRIGGER trg_%I_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION set_updated_at()',
                       t.table_name, t.table_name);
    END LOOP;
END;
$$;
