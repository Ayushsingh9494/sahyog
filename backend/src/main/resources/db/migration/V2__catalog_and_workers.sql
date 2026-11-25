-- Service categories, in the cooperative's display order. The app picks an icon and a translated
-- label by id; `name` is the fallback for categories the app doesn't know yet.
CREATE TABLE categories (
    id       text    PRIMARY KEY,
    name     text    NOT NULL,
    -- Search synonyms, including Hindi words.
    keywords text[]  NOT NULL DEFAULT '{}',
    position integer NOT NULL
);

INSERT INTO categories (id, name, keywords, position) VALUES
    ('electrician', 'Electrician', ARRAY['electric', 'wiring', 'fan', 'light', 'switch', 'बिजली', 'इलेक्ट्रीशियन', 'मिस्त्री'], 0),
    ('plumber', 'Plumber', ARRAY['pipe', 'tap', 'leak', 'water', 'नल', 'प्लंबर', 'पानी'], 1),
    ('carpenter', 'Carpenter', ARRAY['wood', 'furniture', 'door', 'बढ़ई', 'कारपेंटर', 'फर्नीचर'], 2),
    ('painter', 'Painter', ARRAY['paint', 'wall', 'colour', 'पेंटर', 'रंगाई', 'पुताई'], 3),
    ('cleaning', 'Cleaning', ARRAY['cleaner', 'deep clean', 'maid', 'सफाई', 'सफ़ाई'], 4),
    ('appliance_repair', 'Appliance repair', ARRAY['washing machine', 'fridge', 'microwave', 'RO', 'मशीन', 'फ्रिज', 'रिपेयर'], 5),
    ('ac_repair', 'AC repair', ARRAY['air conditioner', 'cooling', 'एसी'], 6),
    ('pest_control', 'Pest control', ARRAY['cockroach', 'termite', 'insects', 'कीट', 'दीमक', 'कॉकरोच'], 7);

-- What a worker offers. One row per user with the Worker role who filled in the profile form.
CREATE TABLE worker_profiles (
    uid              text        PRIMARY KEY REFERENCES users (uid) ON DELETE CASCADE,
    about            text        NOT NULL DEFAULT '',
    locality         text        NOT NULL,
    languages        text[]      NOT NULL,
    years_experience integer     NOT NULL CHECK (years_experience BETWEEN 0 AND 60),
    is_available     boolean     NOT NULL DEFAULT true,
    -- Where they work from, rounded to ~1 km before storing (often their home).
    location         geography(Point, 4326),
    updated_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX worker_profiles_location_idx ON worker_profiles USING gist (location);

CREATE TABLE worker_categories (
    uid         text NOT NULL REFERENCES worker_profiles (uid) ON DELETE CASCADE,
    category_id text NOT NULL REFERENCES categories (id),
    PRIMARY KEY (uid, category_id)
);

CREATE TABLE worker_services (
    uid         text    NOT NULL REFERENCES worker_profiles (uid) ON DELETE CASCADE,
    position    integer NOT NULL,
    name        text    NOT NULL,
    price_paise bigint  NOT NULL CHECK (price_paise > 0),
    unit        text    NOT NULL CHECK (unit IN ('PerVisit', 'PerHour', 'PerJob')),
    PRIMARY KEY (uid, position)
);

-- Identity documents submitted for verification. The image is in private storage (never a
-- public URL); only cooperative admins can read it. Each submission is kept for the audit trail.
CREATE TABLE id_documents (
    id               bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uid              text        NOT NULL REFERENCES users (uid) ON DELETE CASCADE,
    document_type    text        NOT NULL CHECK (document_type IN ('Aadhaar', 'VoterId', 'DrivingLicence', 'Pan')),
    storage_key      text        NOT NULL,
    content_type     text        NOT NULL,
    submitted_at     timestamptz NOT NULL DEFAULT now(),
    -- The admin's decision on this document (null while pending).
    decision         text        CHECK (decision IN ('Approved', 'Rejected')),
    decided_by       text        REFERENCES users (uid),
    decided_at       timestamptz,
    rejection_reason text
);
CREATE INDEX id_documents_uid_idx ON id_documents (uid, submitted_at DESC);
