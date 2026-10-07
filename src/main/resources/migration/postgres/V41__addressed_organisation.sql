
SET LOCAL lock_timeout = '10s';

DROP INDEX IF EXISTS idx_court_document_unaddressed_email_norm;

DO
$$
    DECLARE
        dependent TEXT;
    BEGIN
        SELECT string_agg(DISTINCT d.objid::regclass::text, ', ')
        INTO dependent
        FROM pg_depend d
                 JOIN pg_class index_class ON index_class.oid = d.objid AND index_class.relkind = 'i'
                 JOIN pg_attribute a ON a.attrelid = d.refobjid AND a.attnum = d.refobjsubid
        WHERE d.classid = 'pg_class'::regclass
          AND d.refclassid = 'pg_class'::regclass
          AND d.refobjid = 'court_document'::regclass
          AND a.attname = 'delivery_source';

        IF dependent IS NOT NULL THEN
            RAISE EXCEPTION 'Index(es) % depend on court_document.delivery_source and would be rebuilt under an exclusive lock. Drop them first, and rebuild them after this migration without blocking writes.', dependent;
        END IF;
    END
$$;

ALTER TABLE court_document
    RENAME COLUMN delivery_source TO addressed_organisation;
DO
$$
    DECLARE
        check_name TEXT;
    BEGIN
        FOR check_name IN
            SELECT c.conname
            FROM pg_constraint c
                     JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
            WHERE c.conrelid = 'court_document'::regclass
              AND c.contype = 'c'
              AND a.attname = 'addressed_organisation'
            LOOP
                EXECUTE format('ALTER TABLE court_document DROP CONSTRAINT %I', check_name);
            END LOOP;
    END
$$;

DO
$$
    BEGIN
        IF EXISTS (SELECT 1
                   FROM pg_attribute
                   WHERE attrelid = 'court_document'::regclass
                     AND attname = 'addressed_organisation'
                     AND atttypid = 'character varying'::regtype
                     AND atttypmod - 4 < 32) THEN
            ALTER TABLE court_document
                ALTER COLUMN addressed_organisation TYPE VARCHAR(32);
        END IF;
    END
$$;

ALTER TABLE court_document
    ADD CONSTRAINT fk_court_document_addressed_organisation
        FOREIGN KEY (addressed_organisation) REFERENCES delivery_category (code)
        NOT VALID;

COMMENT ON COLUMN court_document.addressed_organisation IS
    'Delivery category (delivery_category.code) of the organisation the document was addressed to, from prison_email_mapping.category_code for its delivery address. Unmapped geoamey and serco escort mailboxes fall back to PECS. Null while the address is unclassified. Mirrored to the document store as addressedOrganisation metadata, and as deliverySource for PRISON and PECS only.';

ALTER TABLE court_document
    ADD COLUMN delivery_source VARCHAR(10);

COMMENT ON COLUMN court_document.delivery_source IS
    'Transitional. Written only by application versions before V41 while a rolling deploy is in progress. Copied into addressed_organisation and dropped by V44.';
