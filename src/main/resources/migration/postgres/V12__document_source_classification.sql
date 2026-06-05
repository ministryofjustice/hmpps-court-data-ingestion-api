-- Persist where each document was delivered from (PRISON incl YCS, or PECS) and make
-- prison_email_mapping the source of truth for that classification.

ALTER TABLE court_document
    ADD COLUMN IF NOT EXISTS delivery_source VARCHAR(10);

ALTER TABLE court_document
    ADD CONSTRAINT ck_court_document_delivery_source
        CHECK (delivery_source IS NULL OR delivery_source IN ('PRISON', 'PECS'));

COMMENT ON COLUMN court_document.delivery_source IS
    'Where the document was delivered from: PRISON (incl YCS) or PECS (Prisoner Escort and Custody Service). Resolved from prison_email_mapping.source_type during ingestion, with geoamey/serco email suffixes as a backstop. Mirrored to the document store as deliverySource metadata.';

ALTER TABLE prison_email_mapping
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(10);

ALTER TABLE prison_email_mapping
    ALTER COLUMN prison_code DROP NOT NULL;

-- Every existing mapping is, by construction, a prison mailbox.
UPDATE prison_email_mapping
SET source_type = 'PRISON'
WHERE source_type IS NULL;

ALTER TABLE prison_email_mapping
    ALTER COLUMN source_type SET NOT NULL;

ALTER TABLE prison_email_mapping
    ADD CONSTRAINT ck_prison_email_mapping_source_type
        CHECK (source_type IN ('PRISON', 'PECS'));

-- A PRISON mapping must resolve to a prison code; a PECS mapping need not.
ALTER TABLE prison_email_mapping
    ADD CONSTRAINT ck_prison_email_mapping_prison_code
        CHECK (source_type <> 'PRISON' OR prison_code IS NOT NULL);

COMMENT ON COLUMN prison_email_mapping.source_type IS
    'Delivery source for documents arriving at this mailbox: PRISON (incl YCS) or PECS. Primary source of truth; ingestion falls back to geoamey/serco email-suffix heuristics when a mailbox is not listed here.';
COMMENT ON COLUMN prison_email_mapping.prison_code IS
    'Prison code this mailbox maps to. Null for PECS mailboxes, which do not resolve to a prison.';