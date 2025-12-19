alter table document
    add column key text not null default concat('' || random()) unique;
