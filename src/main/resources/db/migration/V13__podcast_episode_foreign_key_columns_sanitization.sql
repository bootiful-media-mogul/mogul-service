alter table podcast_episode
    rename column graphic to graphic_managed_file_id;
alter table podcast_episode
    rename column produced_audio to produced_audio_managed_file_id;
alter table podcast_episode
    rename column produced_graphic to produced_graphic_managed_file_id;
alter table podcast_episode
    rename column podcast to podcast_id;