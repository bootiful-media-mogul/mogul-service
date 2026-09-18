-- job execution now belongs to JobRunr, which keeps its own tables (jobrunr_jobs and
-- friends) and provides the persistence, the hand-off to exactly one node, and the
-- retries that these tables and the modulith event sweep used to approximate.
--
-- job_execution rows were never history worth keeping: a row existed either as an
-- unstarted draft holding the parameters a mogul last typed, or as a record that a job
-- had run. the drafts are gone by design and the records are JobRunr's now.
DROP TABLE IF EXISTS job_execution_param;
DROP TABLE IF EXISTS job_execution;
DROP TABLE IF EXISTS job_param;
DROP TABLE IF EXISTS job;
