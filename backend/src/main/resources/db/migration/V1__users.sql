-- PostGIS for "workers near me" (used from V2 on).
CREATE EXTENSION IF NOT EXISTS postgis;

-- A person on the platform, keyed by their Firebase uid. Roles live here, never in the app:
-- the server is the only source of truth for what someone may do.
CREATE TABLE users (
    uid           text        PRIMARY KEY,
    phone_number  text        NOT NULL,
    name          text        NOT NULL,
    is_customer   boolean     NOT NULL DEFAULT false,
    is_worker     boolean     NOT NULL DEFAULT false,
    -- Cooperative staff who verify workers. Granted by hand (SQL), never through the API.
    is_admin      boolean     NOT NULL DEFAULT false,
    worker_status text        CHECK (worker_status IN ('NotSubmitted', 'Pending', 'Verified', 'Rejected')),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT users_has_a_role CHECK (is_customer OR is_worker OR is_admin),
    CONSTRAINT users_worker_status_iff_worker CHECK (is_worker = (worker_status IS NOT NULL))
);
