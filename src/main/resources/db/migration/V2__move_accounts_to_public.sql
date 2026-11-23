-- Rename the table and its dependent objects to avoid the legacy public.app_user.
ALTER TABLE moba_auth.app_user RENAME TO auth_user;
ALTER TABLE moba_auth.auth_user RENAME CONSTRAINT app_user_pkey TO auth_user_pkey;
ALTER TABLE moba_auth.auth_user RENAME CONSTRAINT app_user_email_key TO auth_user_email_key;
ALTER SEQUENCE moba_auth.app_user_id_seq RENAME TO auth_user_id_seq;
ALTER TABLE moba_auth.auth_user SET SCHEMA public;
