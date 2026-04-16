CREATE TABLE startup_lock (
  lock_name         VARCHAR(100)         PRIMARY KEY,
  locked_at         TIMESTAMP            NOT NULL
);