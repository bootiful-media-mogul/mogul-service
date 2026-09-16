-- every read of an episode's segments filters on podcast_episode_id: the segments
-- batch mapping, and now the duration aggregate that sums them. V8 indexed this
-- table's two managed-file foreign keys but not the one it's actually queried by, so
-- both of those seq-scanned the whole segment table.
create index if not exists podcast_episode_segment_podcast_episode_id_index
    on podcast_episode_segment (podcast_episode_id);
