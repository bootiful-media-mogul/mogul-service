-- an outcome is usually just a link. for the publications whose payload is itself
-- worth a glance -- the text of a tweet, the title of a post -- the plugin can hand
-- back a short, already-abbreviated rendition of what went out, and the UI shows it
-- next to the link.
alter table publication_outcome
    add column preview text;
