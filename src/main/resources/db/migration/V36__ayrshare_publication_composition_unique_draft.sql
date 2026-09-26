-- there must only ever be one *draft* ayrshare_publication_composition per
-- (mogul, platform): the draft row is what the client renders as a social media
-- target, so a second one shows the same platform twice, forever.
--
-- nothing stopped two concurrent reconciliations from each inserting the missing
-- drafts, so collapse any duplicates that already snuck in. keep whichever
-- duplicate the mogul actually attached media to, falling back to the oldest.

with ranked as (select apc.id,
                       row_number() over (
                           partition by apc.mogul_id, apc.platform
                           order by (select count(*)
                                     from composition_attachment ca
                                     where ca.composition_id = apc.composition_id) desc,
                               apc.id
                           ) as rn
                from ayrshare_publication_composition apc
                where apc.draft = true)
delete
from ayrshare_publication_composition
where id in (select id from ranked where rn > 1);

-- non-draft rows are the historical record of what was published, so there are
-- legitimately many of those per platform. only the drafts are constrained.
create unique index ayrshare_publication_composition_one_draft_per_platform_uq
    on ayrshare_publication_composition (mogul_id, platform) where draft;
