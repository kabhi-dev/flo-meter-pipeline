-- Target schema for the generated INSERT statements (PostgreSQL).
-- gen_random_uuid() requires the pgcrypto extension on older Postgres versions;
-- it is built-in from PostgreSQL 13+.
-- CREATE EXTENSION IF NOT EXISTS pgcrypto;

create table if not exists meter_readings (
    id uuid default gen_random_uuid() not null,
    "nmi" varchar(10) not null,
    "timestamp" timestamp not null,
    "consumption" numeric not null,
    constraint meter_readings_pk primary key (id),
    constraint meter_readings_unique_consumption unique ("nmi", "timestamp")
);

