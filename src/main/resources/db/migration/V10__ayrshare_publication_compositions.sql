create table ayrshare_publication_composition
(
    id             serial primary key,
    publication_id bigint  null references publication (id),
    composition_id bigint  null references composition (id),
    mogul_id       bigint  not null references mogul (id),
    platform       text    not null,
    draft          boolean not null default true
);