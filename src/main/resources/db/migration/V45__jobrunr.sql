-- RUN THE FOLLOWING COMMAND TO GET JOBRUNR TO GENERATE THE DDL FOR FLYWAY
-- java -cp ~/.m2/repository/org/jobrunr/jobrunr/8.7.0/jobrunr-8.7.0.jar \
--   org.jobrunr.storage.sql.common.DatabaseSqlMigrationFileProvider postgres
--
--
-- ----------------------------------------
-- file: v000__create_migrations_table.sql
-- ----------------------------------------
CREATE TABLE jobrunr_migrations
(
    id          nchar(36) PRIMARY KEY,
    script      varchar(64) NOT NULL,
    installedOn varchar(29) NOT NULL
);

-- ----------------------------------------
-- file: v001__create_job_table.sql
-- ----------------------------------------
CREATE TABLE jobrunr_jobs
(
    id           NCHAR(36) PRIMARY KEY,
    version      int          NOT NULL,
    jobAsJson    text         NOT NULL,
    jobSignature VARCHAR(512) NOT NULL,
    state        VARCHAR(36)  NOT NULL,
    createdAt    TIMESTAMP    NOT NULL,
    updatedAt    TIMESTAMP    NOT NULL,
    scheduledAt  TIMESTAMP
);
CREATE INDEX jobrunr_state_idx ON jobrunr_jobs (state);
CREATE INDEX jobrunr_job_signature_idx ON jobrunr_jobs (jobSignature);
CREATE INDEX jobrunr_job_created_at_idx ON jobrunr_jobs (createdAt);
CREATE INDEX jobrunr_job_updated_at_idx ON jobrunr_jobs (updatedAt);
CREATE INDEX jobrunr_job_scheduled_at_idx ON jobrunr_jobs (scheduledAt);

-- ----------------------------------------
-- file: v002__create_recurring_job_table.sql
-- ----------------------------------------
CREATE TABLE jobrunr_recurring_jobs
(
    id        NCHAR(128) PRIMARY KEY,
    version   int  NOT NULL,
    jobAsJson text NOT NULL
);

-- ----------------------------------------
-- file: v003__create_background_job_server_table.sql
-- ----------------------------------------
CREATE TABLE jobrunr_backgroundjobservers
(
    id                     NCHAR(36) PRIMARY KEY,
    workerPoolSize         int           NOT NULL,
    pollIntervalInSeconds  int           NOT NULL,
    firstHeartbeat         TIMESTAMP(6)  NOT NULL,
    lastHeartbeat          TIMESTAMP(6)  NOT NULL,
    running                int           NOT NULL,
    systemTotalMemory      BIGINT        NOT NULL,
    systemFreeMemory       BIGINT        NOT NULL,
    systemCpuLoad          NUMERIC(3, 2) NOT NULL,
    processMaxMemory       BIGINT        NOT NULL,
    processFreeMemory      BIGINT        NOT NULL,
    processAllocatedMemory BIGINT        NOT NULL,
    processCpuLoad         NUMERIC(3, 2) NOT NULL
);
CREATE INDEX jobrunr_bgjobsrvrs_fsthb_idx ON jobrunr_backgroundjobservers (firstHeartbeat);
CREATE INDEX jobrunr_bgjobsrvrs_lsthb_idx ON jobrunr_backgroundjobservers (lastHeartbeat);

-- ----------------------------------------
-- file: v004__create_job_stats_view.sql
-- ----------------------------------------
CREATE TABLE jobrunr_job_counters
(
    name   NCHAR(36) PRIMARY KEY,
    amount int NOT NULL
);

INSERT INTO jobrunr_job_counters (name, amount)
VALUES ('AWAITING', 0);
INSERT INTO jobrunr_job_counters (name, amount)
VALUES ('SCHEDULED', 0);
INSERT INTO jobrunr_job_counters (name, amount)
VALUES ('ENQUEUED', 0);
INSERT INTO jobrunr_job_counters (name, amount)
VALUES ('PROCESSING', 0);
INSERT INTO jobrunr_job_counters (name, amount)
VALUES ('FAILED', 0);
INSERT INTO jobrunr_job_counters (name, amount)
VALUES ('SUCCEEDED', 0);

CREATE VIEW jobrunr_jobs_stats
AS
SELECT count(*)                                                                           AS total,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'AWAITING')             AS awaiting,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'SCHEDULED')            AS scheduled,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'ENQUEUED')             AS enqueued,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'PROCESSING')           AS processing,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'FAILED')               AS failed,
       (SELECT((SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'SUCCEEDED') +
               (SELECT amount FROM jobrunr_job_counters jc WHERE jc.name = 'SUCCEEDED'))) AS succeeded,
       (SELECT count(*) FROM jobrunr_backgroundjobservers)                                AS nbrOfBackgroundJobServers,
       (SELECT count(*) FROM jobrunr_recurring_jobs)                                      AS nbrOfRecurringJobs
FROM jobrunr_jobs j;

-- ----------------------------------------
-- file: v005__update_job_stats_view.sql
-- ----------------------------------------
DROP VIEW jobrunr_jobs_stats;

CREATE VIEW jobrunr_jobs_stats
AS
SELECT count(*)                                                                           AS total,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'AWAITING')             AS awaiting,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'SCHEDULED')            AS scheduled,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'ENQUEUED')             AS enqueued,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'PROCESSING')           AS processing,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'FAILED')               AS failed,
       (SELECT((SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'SUCCEEDED') +
               (SELECT amount FROM jobrunr_job_counters jc WHERE jc.name = 'SUCCEEDED'))) AS succeeded,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'DELETED')              AS deleted,
       (SELECT count(*) FROM jobrunr_backgroundjobservers)                                AS nbrOfBackgroundJobServers,
       (SELECT count(*) FROM jobrunr_recurring_jobs)                                      AS nbrOfRecurringJobs
FROM jobrunr_jobs j;

-- ----------------------------------------
-- file: v006__alter_table_jobs_add_recurringjob.sql
-- ----------------------------------------
ALTER TABLE jobrunr_jobs
    ADD recurringJobId VARCHAR(128);
CREATE INDEX jobrunr_job_rci_idx ON jobrunr_jobs (recurringJobId);

-- ----------------------------------------
-- file: v007__alter_table_backgroundjobserver_add_delete_config.sql
-- ----------------------------------------
ALTER TABLE jobrunr_backgroundjobservers
    ADD deleteSucceededJobsAfter VARCHAR(32);
ALTER TABLE jobrunr_backgroundjobservers
    ADD permanentlyDeleteJobsAfter VARCHAR(32);

-- ----------------------------------------
-- file: v008__alter_table_jobs_increase_jobAsJson_size.sql
-- ----------------------------------------
-- Empty migration so all databases follow the same numbering;

-- ----------------------------------------
-- file: v009__change_jobrunr_job_counters_to_jobrunr_metadata.sql
-- ----------------------------------------
CREATE TABLE jobrunr_metadata
(
    id        varchar(156) PRIMARY KEY,
    name      varchar(92) NOT NULL,
    owner     varchar(64) NOT NULL,
    value     text        NOT NULL,
    createdAt TIMESTAMP   NOT NULL,
    updatedAt TIMESTAMP   NOT NULL
);

INSERT INTO jobrunr_metadata (id, name, owner, value, createdAt, updatedAt)
VALUES ('succeeded-jobs-counter-cluster', 'succeeded-jobs-counter', 'cluster',
        cast((SELECT amount FROM jobrunr_job_counters WHERE name = 'SUCCEEDED') AS char(10)), CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

DROP VIEW jobrunr_jobs_stats;
DROP TABLE jobrunr_job_counters;

CREATE VIEW jobrunr_jobs_stats
AS
SELECT count(*)                                                                 AS total,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'AWAITING')   AS awaiting,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'SCHEDULED')  AS scheduled,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'ENQUEUED')   AS enqueued,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'PROCESSING') AS processing,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'FAILED')     AS failed,
       (SELECT((SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'SUCCEEDED') +
               (SELECT cast(cast(value AS char(10)) AS decimal(10, 0))
                FROM jobrunr_metadata jm
                WHERE jm.id = 'succeeded-jobs-counter-cluster')))               AS succeeded,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'DELETED')    AS deleted,
       (SELECT count(*) FROM jobrunr_backgroundjobservers)                      AS nbrOfBackgroundJobServers,
       (SELECT count(*) FROM jobrunr_recurring_jobs)                            AS nbrOfRecurringJobs
FROM jobrunr_jobs j;

-- ----------------------------------------
-- file: v010__change_job_stats.sql
-- ----------------------------------------
DROP VIEW jobrunr_jobs_stats;

CREATE VIEW jobrunr_jobs_stats
AS
SELECT count(*)                                                                 AS total,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'AWAITING')   AS awaiting,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'SCHEDULED')  AS scheduled,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'ENQUEUED')   AS enqueued,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'PROCESSING') AS processing,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'FAILED')     AS failed,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'SUCCEEDED')  AS succeeded,
       (SELECT cast(cast(value AS char(10)) AS decimal(10, 0))
        FROM jobrunr_metadata jm
        WHERE jm.id = 'succeeded-jobs-counter-cluster')                         AS allTimeSucceeded,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'DELETED')    AS deleted,
       (SELECT count(*) FROM jobrunr_backgroundjobservers)                      AS nbrOfBackgroundJobServers,
       (SELECT count(*) FROM jobrunr_recurring_jobs)                            AS nbrOfRecurringJobs
FROM jobrunr_jobs j;

-- ----------------------------------------
-- file: v011__change_sqlserver_text_to_varchar.sql
-- ----------------------------------------
-- Empty migration so all databases follow the same numbering;

-- ----------------------------------------
-- file: v012__change_oracle_alter_jobrunr_metadata_column_size.sql
-- ----------------------------------------
-- Empty migration so all databases follow the same numbering;

-- ----------------------------------------
-- file: v013__alter_table_recurring_job_add_createdAt.sql
-- ----------------------------------------
ALTER TABLE jobrunr_recurring_jobs
    ADD createdAt BIGINT NOT NULL DEFAULT '0';
CREATE INDEX jobrunr_recurring_job_created_at_idx ON jobrunr_recurring_jobs (createdAt);

-- ----------------------------------------
-- file: v014__improve_job_stats.sql
-- ----------------------------------------
DROP VIEW jobrunr_jobs_stats;
CREATE VIEW jobrunr_jobs_stats
AS
with job_stat_results AS (SELECT state, count(*) AS count
    FROM jobrunr_jobs
    GROUP BY ROLLUP (state
)
)
SELECT coalesce((SELECT count FROM job_stat_results WHERE state IS NULL), 0)        AS total,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'SCHEDULED'), 0)  AS scheduled,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'ENQUEUED'), 0)   AS enqueued,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'PROCESSING'), 0) AS processing,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'FAILED'), 0)     AS failed,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'SUCCEEDED'), 0)  AS succeeded,
       coalesce((SELECT cast(cast(value AS char(10)) AS decimal(10, 0))
                 FROM jobrunr_metadata jm
                 WHERE jm.id = 'succeeded-jobs-counter-cluster'), 0)                AS allTimeSucceeded,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'DELETED'), 0)    AS deleted,
       (SELECT count(*) FROM jobrunr_backgroundjobservers)                          AS nbrOfBackgroundJobServers,
       (SELECT count(*) FROM jobrunr_recurring_jobs)                                AS nbrOfRecurringJobs;

DROP INDEX jobrunr_job_updated_at_idx;
CREATE INDEX jobrunr_jobs_state_updated_idx ON jobrunr_jobs (state ASC, updatedAt ASC);

-- ----------------------------------------
-- file: v015__alter_table_backgroundjobserver_add_name.sql
-- ----------------------------------------
ALTER TABLE jobrunr_backgroundjobservers
    ADD name VARCHAR(128);

-- ----------------------------------------
-- file: v016__alter_jobs_stats_add_awaiting_jobs.sql
-- ----------------------------------------
DROP VIEW jobrunr_jobs_stats;
CREATE VIEW jobrunr_jobs_stats
AS
with job_stat_results AS (SELECT state, count(*) AS count
    FROM jobrunr_jobs
    GROUP BY state
)
SELECT coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results), 0)                            AS total,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'AWAITING'), 0)   AS awaiting,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'SCHEDULED'), 0)  AS scheduled,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'ENQUEUED'), 0)   AS enqueued,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'PROCESSING'), 0) AS processing,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'PROCESSED'), 0)  AS processed,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'FAILED'), 0)     AS failed,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'SUCCEEDED'), 0)  AS succeeded,
       coalesce((SELECT cASt(cASt(value AS char(10)) AS decimal(10, 0))
                 FROM jobrunr_metadata jm
                 WHERE jm.id = 'succeeded-jobs-counter-cluster'),
                0)                                                                                        AS allTimeSucceeded,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'DELETED'), 0)    AS deleted,
       (SELECT count(*) FROM jobrunr_backgroundjobservers)                                                AS nbrOfBackgroundJobServers,
       (SELECT count(*) FROM jobrunr_recurring_jobs)                                                      AS nbrOfRecurringJobs;

-- ----------------------------------------
-- file: jobrunr.sql
-- ----------------------------------------


