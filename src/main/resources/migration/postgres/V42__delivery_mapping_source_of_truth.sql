SET LOCAL lock_timeout = '10s';

UPDATE court_document cd
SET delivery_mapping_id = mapping.id
FROM (SELECT id,
             lower(trim(email))                                  AS email,
             count(*) OVER (PARTITION BY lower(trim(email)))     AS claims
      FROM prison_email_mapping) mapping
WHERE cd.delivery_mapping_id IS NULL
  AND lower(trim(cd.prison_email_address)) = mapping.email
  AND mapping.claims = 1;

COMMENT ON COLUMN court_document.delivery_mapping_id IS
    'Source of truth for how the document was addressed: the prison_email_mapping for its delivery address. Take the category (category_code) and prison (prison_code) from the mapping. Null while the address is unclassified.';

COMMENT ON COLUMN court_document.addressed_prison IS
    'Deprecated for new uses. A copy of the prison from the mapping when the document was classified at ingestion or by the re-resolve backfill; it does not follow later changes to the mapping. Derive the prison from delivery_mapping_id instead.';

COMMENT ON COLUMN court_document.delivery_source IS
    'Deprecated. Only ever PRISON or PECS. Derive the category from delivery_mapping_id and prison_email_mapping.category_code instead.';
