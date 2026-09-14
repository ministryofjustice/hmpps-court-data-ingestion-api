
-- Index used to find duplicate HMCTS hearing IDs
CREATE INDEX IF NOT EXISTS idx_court_hearing_hmcts_court_hearing_id
    ON court_hearing (hmcts_court_hearing_id);

-- Index used to efficiently find documents referencing a hearing
CREATE INDEX IF NOT EXISTS idx_court_document_court_hearing_id
    ON court_document (court_hearing_id);

-- Re-point documents from duplicate hearings to the hearing we are keeping.
WITH hearing_mapping AS (
    SELECT
        id AS duplicate_id,
        FIRST_VALUE(id) OVER (
            PARTITION BY hmcts_court_hearing_id
            ORDER BY id
        ) AS keep_id
    FROM court_hearing
    WHERE hmcts_court_hearing_id IS NOT NULL
)
UPDATE court_document cd
SET court_hearing_id = hm.keep_id
    FROM hearing_mapping hm
WHERE cd.court_hearing_id = hm.duplicate_id
  AND hm.duplicate_id <> hm.keep_id;


-- Delete the duplicate hearings.
WITH hearing_mapping AS (
    SELECT
        id AS duplicate_id,
        FIRST_VALUE(id) OVER (
            PARTITION BY hmcts_court_hearing_id
            ORDER BY id
        ) AS keep_id
    FROM court_hearing
    WHERE hmcts_court_hearing_id IS NOT NULL
)
DELETE FROM court_hearing ch
    USING hearing_mapping hm
WHERE ch.id = hm.duplicate_id
  AND hm.duplicate_id <> hm.keep_id;


-- Prevent duplicates in future.
ALTER TABLE court_hearing
    ADD CONSTRAINT court_hearing_hmcts_court_hearing_id_unique
        UNIQUE (hmcts_court_hearing_id);