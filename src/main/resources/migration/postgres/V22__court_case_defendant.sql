CREATE TABLE court_case_defendant
(
    defendant_id         uuid                     NOT NULL CONSTRAINT court_case_defendant_pk PRIMARY KEY,
    case_reference       varchar(255)             NOT NULL,
    master_defendant_id  uuid                     NOT NULL,
    name                 varchar(255)             NULL,
    date_of_birth        date                     NULL,
    retrieved_at         timestamp                NOT NULL,
    source               varchar(64)              NOT NULL
);

-- defendant_id is the key: it identifies the defendant on a case, and identity (name, dob, and
-- later CRO/CRN) is what that case recorded for them. defendant_id does not change.
-- master_defendant_id and case_reference are references the row carries, so both are plain indexes.

-- (master_defendant_id) and therefore (master_defendant_id, case_reference) is unique in
-- Common Platform's model: a person appears once per case. It is deliberately NOT enforced
-- as a unique constraint here, because CP can repoint associations (unmatch/rematch via the CP UI),
-- so a legitimate correction can transiently collide on this pair. We observe the invariant and
-- log changes rather than rejecting the write.

CREATE INDEX court_case_defendant_master ON court_case_defendant (master_defendant_id);
CREATE INDEX court_case_defendant_master_and_case
    ON court_case_defendant (master_defendant_id, case_reference);
CREATE INDEX court_case_defendant_case_reference ON court_case_defendant (case_reference);
