alter table blog_post_preview rename to blog_post_publication_preview;
alter table blog_post_publication_preview add column publication_id bigint not null default 0 references publication(id);
