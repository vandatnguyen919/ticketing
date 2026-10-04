ALTER TABLE users ADD COLUMN password_hash VARCHAR(100);

ALTER TABLE users DROP CONSTRAINT users_provider_check;

ALTER TABLE users ADD CONSTRAINT users_provider_check CHECK (provider IN ('GITHUB', 'GOOGLE', 'EMAIL'));
