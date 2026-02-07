create table if not exists blog_post_preview
(
    id              serial primary key,
    mogul_id        bigint references mogul (id),
    blog_post_id         bigint references blog_post (id),
    managed_file_id bigint references managed_file (id)
) ;