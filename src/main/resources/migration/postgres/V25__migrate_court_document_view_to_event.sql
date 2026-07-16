
alter table public.court_document_view
    rename to court_document_view_event;

alter table public.court_document_view_event
    rename column viewed_at to occurred_at;

alter table public.court_document_view_event
    rename constraint court_document_view_pk
        to court_document_view_event_pk;

alter table public.court_document_view_event
    rename constraint fk_court_document_view_court_document_case_id
        to fk_court_document_view_event_court_document;

alter table public.court_document_view_event
    add column event_type varchar(32);

update public.court_document_view_event
set event_type = 'VIEWED';

alter table public.court_document_view_event
    alter column event_type set not null;

alter table public.court_document_view_event
    add constraint ck_court_document_view_event_type
        check (event_type in ('VIEWED', 'MARKED_NEW'));

create index idx_court_document_view_event_latest
    on public.court_document_view_event (
                                         court_document_id,
                                         occurred_at desc,
                                         id desc
        );