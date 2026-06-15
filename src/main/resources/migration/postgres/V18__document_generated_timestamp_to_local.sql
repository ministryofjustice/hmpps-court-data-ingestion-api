update court_document
set document_generated_timestamp = document_generated_timestamp - interval '1 hour';

alter table court_document
  alter column document_generated_timestamp type timestamp without time zone
    using document_generated_timestamp at time zone 'Europe/London';
