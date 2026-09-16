-- a mogul_status is a calendar day in a mogul's life, not an instant, so the only
-- question it raises is *whose* day. it was the server's: the JVM runs in UTC, so a
-- mogul in Los Angeles filed everything after 5pm under tomorrow, and a mogul in Tokyo
-- would spend most of their day filed under yesterday.
--
-- nullable on purpose. a mogul who has never told us where they are falls back to the
-- server's zone, which is exactly the behaviour they have today.
alter table mogul
    add column if not exists time_zone text;
