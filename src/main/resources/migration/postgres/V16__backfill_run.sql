CREATE TABLE backfill_run (
  run_id         UUID         PRIMARY KEY,
  backfill_id    VARCHAR(64)  NOT NULL,
  status         VARCHAR(16)  NOT NULL,
  cursor         TEXT,
  processed      BIGINT       NOT NULL DEFAULT 0,
  failed         BIGINT       NOT NULL DEFAULT 0,
  started_at     TIMESTAMP    NOT NULL,
  heartbeat_at   TIMESTAMP    NOT NULL,
  completed_at   TIMESTAMP,
  triggered_by   VARCHAR(128),
  failure_reason TEXT
);

CREATE UNIQUE INDEX backfill_run_one_running_per_id
  ON backfill_run (backfill_id)
  WHERE status = 'RUNNING';

CREATE INDEX backfill_run_recent
  ON backfill_run (backfill_id, started_at DESC);
