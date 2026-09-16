-- every timestamp in this schema was `timestamp without time zone`: a wall clock with
-- no record of which zone produced it. that is only interpretable if you know the JVM's
-- zone at the moment of writing, which nothing pinned and no row remembers. redeploying
-- in another region would have silently re-read every existing row as a different
-- instant, and daylight saving gives you one ambiguous hour and one impossible hour a
-- year.
--
-- 'UTC' is not a guess. the production JVM reports UTC, and the PostgreSQL JDBC driver
-- issues `SET TimeZone` to the JVM's default on every connection -- so rows the
-- application inserted and rows that fell back to `default now()` were both written
-- against the same UTC wall clock. verified against the running server before writing
-- this.
--
-- this is behaviour-preserving. the values already read back as UTC instants and are
-- already rendered in the viewer's zone; nothing on screen moves. what changes is that
-- the instant is now recorded rather than inferred.
--
-- mogul_status.date is deliberately absent. it is a calendar day -- "the mogul's
-- sixteenth of September" -- not an instant, and converting it would be wrong. only
-- mogul_status.created, the audit stamp for when the row was written, is converted here.

alter table blog alter column created type timestamptz using created at time zone 'UTC';

alter table blog_post alter column created type timestamptz using created at time zone 'UTC';

alter table composition alter column created type timestamptz using created at time zone 'UTC';

alter table composition_attachment alter column created type timestamptz using created at time zone 'UTC';

alter table job_execution alter column start type timestamptz using start at time zone 'UTC';
alter table job_execution alter column stop type timestamptz using stop at time zone 'UTC';

alter table managed_file alter column created type timestamptz using created at time zone 'UTC';

alter table managed_file_deletion_request alter column created type timestamptz using created at time zone 'UTC';

alter table mogul alter column created type timestamptz using created at time zone 'UTC';
alter table mogul alter column updated type timestamptz using updated at time zone 'UTC';

alter table mogul_status alter column created type timestamptz using created at time zone 'UTC';

alter table note alter column created type timestamptz using created at time zone 'UTC';
alter table note alter column done type timestamptz using done at time zone 'UTC';

alter table podcast alter column created type timestamptz using created at time zone 'UTC';

alter table podcast_episode alter column created type timestamptz using created at time zone 'UTC';
alter table podcast_episode alter column produced_audio_updated type timestamptz using produced_audio_updated at time zone 'UTC';
alter table podcast_episode alter column produced_audio_assets_updated type timestamptz using produced_audio_assets_updated at time zone 'UTC';

alter table publication alter column created type timestamptz using created at time zone 'UTC';
alter table publication alter column published type timestamptz using published at time zone 'UTC';

alter table publication_outcome alter column created type timestamptz using created at time zone 'UTC';

alter table settings alter column created type timestamptz using created at time zone 'UTC';

alter table transcript alter column created type timestamptz using created at time zone 'UTC';
alter table transcript alter column transcribed type timestamptz using transcribed at time zone 'UTC';
