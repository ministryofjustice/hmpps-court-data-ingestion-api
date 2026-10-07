ALTER TABLE court_document
    VALIDATE CONSTRAINT fk_court_document_addressed_organisation;

DO
$$
    DECLARE
        ambiguous TEXT;
    BEGIN
        SELECT string_agg(email, ', ' ORDER BY email)
        INTO ambiguous
        FROM (SELECT lower(trim(email)) AS email
              FROM prison_email_mapping
              GROUP BY lower(trim(email))
              HAVING count(*) > 1) duplicates;

        IF ambiguous IS NOT NULL THEN
            RAISE WARNING 'Not classifying documents for delivery addresses with more than one mapping: %', ambiguous;
        END IF;
    END
$$;

WITH mapping AS (SELECT lower(trim(m.email)) AS email,
                        m.id,
                        m.category_code,
                        CASE WHEN c.requires_prison_code IS FALSE THEN NULL ELSE m.prison_code END AS prison_code,
                        count(*) OVER (PARTITION BY lower(trim(m.email)))                       AS claims
                 FROM prison_email_mapping m
                          LEFT JOIN delivery_category c ON c.code = m.category_code)
UPDATE court_document cd
SET addressed_organisation = mapping.category_code,
    addressed_prison       = mapping.prison_code,
    delivery_mapping_id    = mapping.id
FROM mapping
WHERE lower(trim(cd.prison_email_address)) = mapping.email
  AND mapping.claims = 1
  AND mapping.category_code IS NOT NULL
  AND cd.addressed_organisation IS NULL
  AND cd.addressed_prison IS NULL;
