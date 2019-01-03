alter role postgres password 'admin';
create database high_performance_java_persistence with owner postgres;

\c high_performance_java_persistence postgres;

create schema if not exists auth;
CREATE TYPE auth.user_role AS ENUM (
    'ROLE_ADMIN',
    'ROLE_USER'
);

CREATE TABLE auth.users (
  id BIGSERIAL PRIMARY KEY,
	username VARCHAR(50) NOT NULL UNIQUE,
	roles auth.user_role[] NOT NULL DEFAULT ARRAY['ROLE_USER']::auth.user_role[]
);
