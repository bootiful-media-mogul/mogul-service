alter table blog_post
    add column if not exists rss_slug text null;
