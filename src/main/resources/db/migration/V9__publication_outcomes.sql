create table publication_outcome
(
    id             serial primary key,
    created        timestamp not null default now(),
    success        boolean   not null default false,
    uri            text      null,
    publication_id bigint    not null references publication (id),
    key            text      not null
);


insert into publication_outcome (success, uri, publication_id, key)
select true, url, id, plugin
from publication;

alter table publication
    drop column url;