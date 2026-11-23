ALTER TABLE public.auth_user RENAME TO app_user;
ALTER TABLE public.app_user RENAME CONSTRAINT auth_user_pkey TO app_user_pkey;
ALTER TABLE public.app_user RENAME CONSTRAINT auth_user_email_key TO app_user_email_key;
ALTER SEQUENCE public.auth_user_id_seq RENAME TO app_user_id_seq;
