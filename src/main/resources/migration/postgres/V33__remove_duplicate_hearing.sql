BEGIN;

-- 1. Create a mapping of duplicate hearing IDs -> the hearing ID to keep
CREATE TEMP TABLE hearing_dedup AS
SELECT
    id AS duplicate_id,
    FIRST_VALUE(id) OVER (
        PARTITION BY hmcts_court_hearing_id
        ORDER BY id
    ) AS keep_id
FROM court_hearing
WHERE hmcts_court_hearing_id IS NOT NULL;

-- 2. Update documents that point at a duplicate hearing
UPDATE court_document cd
SET court_hearing_id = hd.keep_id
    FROM hearing_dedup hd
WHERE cd.court_hearing_id = hd.duplicate_id
  AND hd.duplicate_id <> hd.keep_id;

-- 3. Delete the duplicate hearing rows
DELETE FROM court_hearing ch
    USING hearing_dedup hd
WHERE ch.id = hd.duplicate_id
  AND hd.duplicate_id <> hd.keep_id;

-- 4. Add the unique constraint
ALTER TABLE court_hearing
    ADD CONSTRAINT court_hearing_hmcts_id_unique
        UNIQUE (hmcts_court_hearing_id);

COMMIT;
