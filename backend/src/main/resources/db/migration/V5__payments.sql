-- How a booking is paid. The customer picks a method when booking; payment itself happens after
-- the job is completed: online through the payment gateway (UPI, netbanking, cards), or in cash
-- to the worker, who confirms it. The amount is the booking's price.
ALTER TABLE bookings
    ADD COLUMN payment_method     text NOT NULL DEFAULT 'Cash' CHECK (payment_method IN ('Cash', 'Online')),
    ADD COLUMN payment_status     text NOT NULL DEFAULT 'Unpaid' CHECK (payment_status IN ('Unpaid', 'Paid')),
    -- How it was actually paid (a cash booking may end up paid online, and the other way round).
    ADD COLUMN paid_via           text CHECK (paid_via IN ('Cash', 'Online')),
    ADD COLUMN paid_at            timestamptz,
    -- The gateway's order and payment ids, for reconciliation with its dashboard.
    ADD COLUMN gateway_order_id   text,
    ADD COLUMN gateway_payment_id text UNIQUE,
    ADD CONSTRAINT bookings_paid_has_details CHECK ((payment_status = 'Paid') = (paid_via IS NOT NULL AND paid_at IS NOT NULL));
