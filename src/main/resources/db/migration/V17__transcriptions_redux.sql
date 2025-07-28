create table if not exists transcription
(
    id            serial primary key,
    created       timestamp not null default now(),
    transcribed   timestamp null,
    payload_class text      not null,
    payload       text      not null,
    transcript    text      null
);


insert into transcription( payload_class, payload, transcript ,transcribed)
select
       'com.joshlong.mogul.api.podcasts.Segment',
       '"' || peso.id || '"',
       peso.transcript ,
       (select pe.produced_audio_updated
        from podcast p,
             public.podcast_episode pe,
             public.podcast_episode_segment pes
        where pes.podcast_episode_id = pe.id
          and p.id = pe.podcast_id
          and pes.id = peso.id) as mogul_id

from podcast_episode_segment peso;

-- alter table podcast_episode_segment drop column transcript;
-- alter table podcast_episode_segment drop column transcribable;