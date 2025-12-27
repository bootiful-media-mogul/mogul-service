create table if not exists note
(
    mogul_id      bigint    not null references mogul (id),
    id            serial primary key,
    created       timestamp not null default now(),
    payload       text      not null,
    payload_class text      not null,
    url           text      null,
    note          text      not null
);