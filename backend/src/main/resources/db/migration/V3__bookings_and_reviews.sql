-- A customer's booking of one service from one worker; the worker sees it as a "job".
-- Names, service and price are snapshots: later profile edits don't rewrite history.
CREATE TABLE bookings (
    id              text        PRIMARY KEY,
    customer_uid    text        NOT NULL REFERENCES users (uid),
    worker_uid      text        NOT NULL REFERENCES users (uid),
    customer_name   text        NOT NULL,
    worker_name     text        NOT NULL,
    service_name    text        NOT NULL,
    price_paise     bigint      NOT NULL CHECK (price_paise > 0),
    price_unit      text        NOT NULL CHECK (price_unit IN ('PerVisit', 'PerHour', 'PerJob')),
    -- The visit window: a local (India) date and the hour it starts.
    slot_date       date        NOT NULL,
    slot_start_hour integer     NOT NULL CHECK (slot_start_hour BETWEEN 0 AND 23),
    address         text        NOT NULL,
    note            text,
    -- The customer's map pin (~1 m), only ever sent to the customer and the assigned worker.
    latitude        double precision,
    longitude       double precision,
    status          text        NOT NULL CHECK (status IN (
                        'Requested', 'Accepted', 'WorkerEnRoute', 'InProgress',
                        'Completed', 'Cancelled', 'Rejected', 'Expired')),
    -- Shown to the customer once accepted; the worker must get it from them to start the job.
    start_code      text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    completed_at    timestamptz,
    -- The customer's review of this booking (at most one, never edited).
    review_rating   integer     CHECK (review_rating BETWEEN 1 AND 5),
    review_comment  text,
    reviewed_at     timestamptz,
    CONSTRAINT bookings_no_self_booking CHECK (customer_uid <> worker_uid),
    CONSTRAINT bookings_pin_both_or_neither CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT bookings_review_complete CHECK ((review_rating IS NULL) = (reviewed_at IS NULL))
);

-- A worker can't be in two places at once: at most one live booking per worker per slot.
-- The database enforces it, so two customers racing for the same slot can't both win.
CREATE UNIQUE INDEX bookings_one_live_per_worker_slot ON bookings (worker_uid, slot_date, slot_start_hour)
    WHERE status IN ('Requested', 'Accepted', 'WorkerEnRoute', 'InProgress');

CREATE INDEX bookings_customer_idx ON bookings (customer_uid);
CREATE INDEX bookings_worker_idx ON bookings (worker_uid);
CREATE INDEX bookings_reviews_idx ON bookings (worker_uid, reviewed_at DESC) WHERE review_rating IS NOT NULL;
