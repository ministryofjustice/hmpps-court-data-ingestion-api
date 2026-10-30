CREATE TABLE court_schedule_snapshot
(
    id             UUID PRIMARY KEY,
    case_reference VARCHAR(255)                NOT NULL,
    fetched_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    http_status    INTEGER                     NOT NULL,
    body_sha256    VARCHAR(64)                 NULL,
    body           JSONB                       NULL,
    error_body     TEXT                        NULL,
    changed        BOOLEAN                     NOT NULL
);

CREATE INDEX idx_court_schedule_snapshot_case_fetched
    ON court_schedule_snapshot (case_reference, fetched_at);

COMMENT ON TABLE court_schedule_snapshot IS
    E'Description: Raw Court Schedule API responses for a study of how often hearings are re-listed. Populated by the court-schedule-snapshot backfill.\nSource System: HMCTS Court Schedule API';
COMMENT ON COLUMN court_schedule_snapshot.fetched_at IS
    'Europe/London wall-clock time of the fetch.';
COMMENT ON COLUMN court_schedule_snapshot.body IS
    'Response body, stored only when its hash differs from the previous stored body for the case. NULL means unchanged since the last stored body.';
COMMENT ON COLUMN court_schedule_snapshot.changed IS
    'True when this fetch stored a new body (including the first fetch for a case).';
