CREATE TABLE IF NOT EXISTS delivery_category
(
    code                       VARCHAR(32) PRIMARY KEY,
    name                       VARCHAR(128) NOT NULL,
    requires_prison_code       BOOLEAN      NOT NULL DEFAULT FALSE,
    unmatched_needs_review     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by                 VARCHAR(64),
    created_at                 TIMESTAMP    NOT NULL DEFAULT now()
);

INSERT INTO delivery_category (code, name, requires_prison_code, unmatched_needs_review, created_by)
VALUES ('PRISON', 'Prison', TRUE, TRUE, 'V33'),
       ('PECS', 'Escort (PECS)', FALSE, TRUE, 'V33')
ON CONFLICT (code) DO NOTHING;

ALTER TABLE prison_email_mapping
    ALTER COLUMN prison_code DROP NOT NULL;

ALTER TABLE prison_email_mapping
    ADD COLUMN IF NOT EXISTS category_code VARCHAR(32) REFERENCES delivery_category (code),
    ADD COLUMN IF NOT EXISTS created_by    VARCHAR(64),
    ADD COLUMN IF NOT EXISTS created_at    TIMESTAMP NOT NULL DEFAULT now();

UPDATE prison_email_mapping SET category_code = 'PRISON' WHERE category_code IS NULL AND prison_code IS NOT NULL;

UPDATE prison_email_mapping SET category_code = 'PECS' WHERE category_code IS NULL AND source_type = 'PECS';

ALTER TABLE prison_email_mapping
    DROP CONSTRAINT IF EXISTS ck_prison_email_mapping_source_type;

ALTER TABLE prison_email_mapping
    ALTER COLUMN source_type DROP NOT NULL;

ALTER TABLE prison_email_mapping
    ADD CONSTRAINT ck_prison_email_mapping_category_prison_code
        CHECK (category_code <> 'PRISON' OR prison_code IS NOT NULL);

ALTER TABLE court_document
    ADD COLUMN IF NOT EXISTS delivery_mapping_id UUID REFERENCES prison_email_mapping (id);

CREATE INDEX IF NOT EXISTS idx_court_document_delivery_mapping
    ON court_document (delivery_mapping_id)
    WHERE delivery_mapping_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_court_document_unclassified_address
    ON court_document (prison_email_address, ingestion_at)
    WHERE addressed_prison IS NULL;