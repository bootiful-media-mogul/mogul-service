-- V8 was called "referencing_foreign_key_indexes" and meant to cover exactly this, but
-- it predates most of these tables. these two foreign keys are on read paths that run
-- constantly and were seq-scanning without them.

-- read once per publication batch: the row mapper collects every publication in a
-- result set and fetches all of their outcomes in a single `in (...)`. this table grows
-- fastest of any here -- a row per platform per publication, forever.
create index if not exists publication_outcome_publication_id_index
    on publication_outcome (publication_id);

-- backs getPodcastEpisodesByPodcast, which runs on every podcast page.
create index if not exists podcast_episode_podcast_id_index
    on podcast_episode (podcast_id);
