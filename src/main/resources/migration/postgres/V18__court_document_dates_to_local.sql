-- Store the court_document dates as plain `timestamp` holding Europe/London
-- wall-clock, consistent with the rest of CCRD (walking back the V1 timestamptz columns).
--
-- PRECONDITION: assumes the pre-fix data. The `-1h` below is the SAME correction as PR #109's V18
-- and must run exactly once. Do NOT apply this after that migration has already run in an env.
--
-- document_generated_timestamp was stored an hour ahead by the old offset-dropping parse, so the
-- instant is corrected first. ingestion_at / identified_at were written by LocalDateTime.now() on
-- the Europe/London pod, so they already hold the right instant and only need the representation
-- change. The ALTER rewrites court_document under an ACCESS EXCLUSIVE lock; schedule if large.

update court_document
set document_generated_timestamp = document_generated_timestamp + interval '1 hour';

alter table court_document
    alter column document_generated_timestamp type timestamp without time zone
        using document_generated_timestamp at time zone 'Europe/London',
    alter column ingestion_at type timestamp without time zone
        using ingestion_at at time zone 'Europe/London',
    alter column identified_at type timestamp without time zone
        using identified_at at time zone 'Europe/London';
