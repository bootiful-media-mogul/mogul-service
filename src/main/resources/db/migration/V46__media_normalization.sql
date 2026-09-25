-- normalization -- transcoding an upload into the one audio or image format the rest of
-- the system assumes -- used to happen inside this process, on a bounded pool of virtual
-- threads that each forked ffmpeg or magick. it now happens in the processors module,
-- which means the api sends a request and gets an answer back some unknown number of
-- seconds later, over a queue, in a different process, possibly after this one has been
-- restarted.
--
-- all the api keeps hold of across that gap is a correlation id. this table is the other
-- end of it: given the correlation id on an incoming reply, it says which ManagedFile the
-- result was written to and what the caller was doing when it asked -- the podcast
-- episode and segment ids that MediaNormalizedEvent has to carry for anything downstream
-- to refresh itself. none of that is sent to the processor, which is given only the
-- buckets and keys it needs to do the work.
--
-- the row is written and committed *before* the request goes on the wire, because for a
-- small enough file the reply can beat the enclosing transaction's commit.

create table if not exists media_normalization
(
    id                     serial primary key,
    -- unique, and so already indexed: this is the only way in from a reply.
    correlation_id         text                     not null unique,
    input_managed_file_id  bigint                   not null references managed_file (id) on delete cascade,
    output_managed_file_id bigint                   not null references managed_file (id) on delete cascade,
    -- the caller's context, verbatim, as json: whatever the caller will need in order to
    -- make sense of the result when it finally arrives.
    context                text                     not null default '{}',
    created                timestamp with time zone not null default now(),
    -- null until a reply lands. the reply queue is at-least-once, so completing a row is
    -- conditional on it still being null, and that is what makes a redelivered reply a
    -- no-op rather than a second MediaNormalizedEvent.
    completed              timestamp with time zone null,
    successful             bool                     null,
    error                  text                     null
);

-- deleting a ManagedFile has to be able to take its normalization records with it, and
-- an un-indexed referencing column makes every such delete a seq scan of this table.
create index if not exists media_normalization_input_managed_file_id_index
    on media_normalization (input_managed_file_id);

create index if not exists media_normalization_output_managed_file_id_index
    on media_normalization (output_managed_file_id);
