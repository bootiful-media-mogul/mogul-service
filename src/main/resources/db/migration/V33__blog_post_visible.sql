alter table blog_post
    add column if not exists visible boolean default false;
-- this will be retroactively true
update blog_post set visible = true;