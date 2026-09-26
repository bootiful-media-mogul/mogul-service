-- a Mogul is an identity, not an artifact. you don't publish a user; you publish
-- content *about* a user, on some particular day.
--
-- publications are already scoped per-publishable, by the soft foreign key of
-- (payload, payload_class). that works for a post or an episode because each new
-- one gets its own key: a handful of publications land against it and then you
-- move on. a Mogul has exactly one key and never ends, so every publication a
-- mogul ever makes about themselves piles into a single bucket that only grows.
--
-- mogul_status is the missing indirection: one row per mogul per day, and that
-- row -- not the mogul -- is what a publication points at. one key per day
-- restores the property every other publishable already has.

create table if not exists mogul_status
(
    id
    serial
    primary
    key,
    mogul_id
    bigint
    not
    null
    references
    mogul
(
    id
),
    date date not null,
    created timestamp not null default now
(
),
    unique
(
    mogul_id,
    date
)
    );

-- rows are created lazily, the first time a mogul does something on a given day.
-- pre-creating one per mogul per day would just relocate the unbounded growth we
-- came here to get rid of, and it would make "the last ten days" mean ten rows
-- rather than ten days that actually had something in them.


-- publications used to take the Mogul itself as their payload, which made every
-- such row say "we published Mogul #1" -- a true statement about nothing in
-- particular. re-point them at the status for the day they were created on, so
-- the history stays readable instead of being deleted.

insert into mogul_status (mogul_id, date)
select distinct p.mogul_id, p.created::date
from publication p
where p.payload_class = 'com.joshlong.mogul.api.mogul.Mogul' on conflict (mogul_id, date) do nothing;

update publication p
set payload = ms.id::text,
    payload_class = 'com.joshlong.mogul.api.mogul.MogulStatus'
from mogul_status ms
where p.payload_class = 'com.joshlong.mogul.api.mogul.Mogul'
  and ms.mogul_id = p.mogul_id
  and ms.date = p.created:: date;

-- nothing to do to ayrshare_publication_composition: it reaches a mogul_status
-- the same way it reaches a post or an episode, through publication_id and the
-- publication's own (payload, payload_class).
