-- Initial schema (spec 8.1). Migrations are additive and forward-only (spec 7.5).
-- Derived data (sample, lap, effort, metrics_snapshot) can be rebuilt from the raw FIT files;
-- user-entered data (segment, drill_type, geozone, manual surface, notes) cannot.

create table drill_type (
    id    uuid primary key,
    code  text not null unique,
    name  text not null,
    kind  text not null check (kind in ('DRILL', 'GAME', 'WARMUP', 'REST')),
    color text not null
);

create table geozone (
    id         uuid primary key,
    name       text not null,
    surface    text not null check (surface in ('GRASS', 'SAND')),
    shape      jsonb not null, -- {"type":"circle","lat":..,"lon":..,"radiusM":..} | GeoJSON Polygon
    created_at timestamptz not null default now()
);

create table session (
    id                  uuid primary key,
    file_sha256         char(64) not null unique,
    file_name           text not null,
    uploaded_at         timestamptz not null default now(),
    start_time          timestamptz not null,
    local_tz_offset_sec int,
    elapsed_sec         int not null,
    timer_sec           int not null,
    distance_m          double precision,
    device              text,
    sport               text,
    sub_sport           text,
    start_lat           double precision,
    start_lon           double precision,
    geozone_id          uuid references geozone (id) on delete set null,
    surface             text not null default 'UNKNOWN'
                        check (surface in ('GRASS', 'SAND', 'UNKNOWN')),
    surface_source      text not null default 'NONE'
                        check (surface_source in ('GEOZONE', 'MANUAL', 'NONE')),
    notes               text,
    analysis_version    int not null
);

create index session_start_time_idx on session (start_time);
create index session_geozone_id_idx on session (geozone_id);

create table sample (
    session_id   uuid not null references session (id) on delete cascade,
    t            int not null,
    lat          double precision,
    lon          double precision,
    speed_raw    real,
    speed        real not null,
    accel        real not null,
    hr           smallint,
    distance_m   real,
    altitude_m   real,
    interpolated boolean not null,
    in_pause     boolean not null,
    primary key (session_id, t)
);

create table lap (
    session_id uuid not null references session (id) on delete cascade,
    idx        int not null,
    start_t    int not null,
    end_t      int not null,
    trigger    text,
    primary key (session_id, idx)
);

create table segment (
    id            uuid primary key,
    session_id    uuid not null references session (id) on delete cascade,
    start_t       int not null,
    end_t         int not null,
    drill_type_id uuid references drill_type (id) on delete set null,
    label         text,
    source        text not null check (source in ('LAP', 'MANUAL')),
    -- Minimum duration and non-overlap (spec 5, invariants 1-2) are enforced in the service layer.
    check (start_t >= 0 and end_t > start_t)
);

create index segment_session_id_idx on segment (session_id);
create index segment_drill_type_id_idx on segment (drill_type_id);

create table effort (
    id         uuid primary key,
    session_id uuid not null references session (id) on delete cascade,
    start_t    int not null,
    peak_t     int not null,
    end_t      int not null,
    segment_id uuid references segment (id) on delete set null,
    metrics    jsonb not null, -- per-effort metrics (spec 6.4)
    check (start_t <= peak_t and peak_t <= end_t)
);

create index effort_session_id_idx on effort (session_id);
create index effort_segment_id_idx on effort (segment_id);

create table metrics_snapshot (
    session_id       uuid not null references session (id) on delete cascade,
    scope            text not null check (scope in ('SESSION', 'SEGMENT')),
    scope_id         uuid not null, -- session id or segment id
    analysis_version int not null,
    metrics          jsonb not null,
    primary key (scope, scope_id)
);

create index metrics_snapshot_session_id_idx on metrics_snapshot (session_id);
