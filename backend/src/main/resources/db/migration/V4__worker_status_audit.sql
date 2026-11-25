-- Every change to a worker's verification status, by whom and why: approvals, rejections and
-- revocations by cooperative admins. Powers the admin dashboard's "recent decisions" and each
-- worker's history, and answers "who let this person in?" later.
CREATE TABLE worker_status_changes (
    id          bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    worker_uid  text        NOT NULL REFERENCES users (uid) ON DELETE CASCADE,
    from_status text,
    to_status   text        NOT NULL CHECK (to_status IN ('NotSubmitted', 'Pending', 'Verified', 'Rejected')),
    -- The admin who decided; null for changes the worker caused (e.g. submitting an ID).
    changed_by  text        REFERENCES users (uid),
    reason      text,
    changed_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX worker_status_changes_worker_idx ON worker_status_changes (worker_uid, changed_at DESC);
CREATE INDEX worker_status_changes_recent_idx ON worker_status_changes (changed_at DESC) WHERE changed_by IS NOT NULL;
