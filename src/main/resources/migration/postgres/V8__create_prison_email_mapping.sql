-- Access control: resolve the HMCTS delivery email to a prison code and cache it on the document.

ALTER TABLE court_document
    ADD COLUMN IF NOT EXISTS addressed_prison VARCHAR(6);

COMMENT ON COLUMN court_document.addressed_prison IS
    'Prison code the document was delivered to, resolved from prison_email_address via prison_email_mapping. Used to limit access. Derived during ingestion, not part of the HMCTS notification.';

CREATE INDEX IF NOT EXISTS idx_court_document_addressed_prison
    ON court_document (addressed_prison);

-- Reference lookup. Single surrogate key for consistency with the other tables; email stays unique.
CREATE TABLE IF NOT EXISTS prison_email_mapping
(
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(320) NOT NULL,
    prison_code VARCHAR(6)   NOT NULL,
    CONSTRAINT uq_prison_email_mapping_email UNIQUE (email)
);

COMMENT ON TABLE prison_email_mapping IS
    'Lookup from delivery email address to prison code. Joined to court_document by email value at write time, never by id.';
COMMENT ON COLUMN prison_email_mapping.id IS 'Surrogate primary key.';
COMMENT ON COLUMN prison_email_mapping.email IS
    'Normalised delivery email address. Unique natural key, matched against court_document.prison_email_address.';
COMMENT ON COLUMN prison_email_mapping.prison_code IS 'Prison code this mailbox maps to.';
