CREATE TABLE IF NOT EXISTS events (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    event_date TIMESTAMP NOT NULL,
    remaining_tickets INTEGER NOT NULL CHECK (remaining_tickets >= 0)
);

CREATE TABLE IF NOT EXISTS app_users (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS bookings (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id),
    user_email VARCHAR(255) NOT NULL,
    booked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO events (title, event_date, remaining_tickets)
SELECT * FROM (
    VALUES
        ('Sunset Jazz Night', '2026-10-12T19:30:00Z'::TIMESTAMP, 24),
        ('City Run 5K', '2026-10-18T08:00:00Z'::TIMESTAMP, 13),
        ('Indie Film Festival', '2026-11-02T18:45:00Z'::TIMESTAMP, 8)
) AS seed(title, event_date, remaining_tickets)
WHERE NOT EXISTS (SELECT 1 FROM events);
