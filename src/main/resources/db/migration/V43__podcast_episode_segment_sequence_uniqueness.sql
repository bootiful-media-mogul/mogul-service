-- a segment's sequence_number decides where it lands in the produced episode, and
-- nothing stopped two of them sharing one.
with duplicated as (select podcast_episode_id
                    from podcast_episode_segment
                    group by podcast_episode_id, sequence_number
                    having count(*) > 1),
     renumbered as (select id,
                           row_number() over (partition by podcast_episode_id
                               order by sequence_number, id) as position
from podcast_episode_segment
where podcast_episode_id in (select podcast_episode_id from duplicated))
update podcast_episode_segment pes
set sequence_number = r.position from renumbered r
where pes.id = r.id
  and pes.sequence_number is distinct
from r.position;

-- we have require that there never be more than one row with an episode id + sequence no.
-- but as we re-order, we cant enforce that check, because at some points
-- some things will be duplicated before the transaction commits.
-- 'deferrable initially deferred ' defers the evaluation of this constraint to the transaction commit.
alter table podcast_episode_segment
    add constraint podcast_episode_segment_episode_sequence_unique
        unique (podcast_episode_id, sequence_number) deferrable initially deferred;
