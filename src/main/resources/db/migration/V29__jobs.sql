CREATE TABLE job
(
    id       SERIAL PRIMARY KEY,
    job_name TEXT NOT NULL UNIQUE
);

CREATE TABLE job_param
(
    id         SERIAL PRIMARY KEY,
    param_name TEXT   NOT NULL,
    job_id     BIGINT NOT NULL REFERENCES job (id),
    unique (job_id, param_name)
);

CREATE TABLE job_execution
(
    id       SERIAL PRIMARY KEY,
    mogul_id BIGINT NOT NULL references mogul (id),
    job_name TEXT   NOT NULL,
    start    timestamp,
    stop     timestamp,
    unique (mogul_id, job_name)
);

CREATE TABLE job_execution_param
(
    id               SERIAL PRIMARY KEY,
    job_execution_id BIGINT NOT NULL REFERENCES job_execution (id),
    param_name       TEXT   NOT NULL,
    param_class      TEXT   NULL,
    param_value      TEXT,
    unique (job_execution_id, param_name)
);