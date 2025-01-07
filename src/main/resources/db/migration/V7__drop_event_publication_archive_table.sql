-- somehow its possible to get duplicate keys when using spring modulith's 
-- archive mode. so we're rolling this change out. 
drop table if exists event_publication_archive ;