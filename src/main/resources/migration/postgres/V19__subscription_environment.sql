ALTER TABLE subscription
    ADD COLUMN environment varchar(10) NULL;

UPDATE subscription SET environment = '${environment}' WHERE subscribed_at IS NOT NULL;

ALTER TABLE subscription
    ALTER COLUMN environment SET NOT NULL;