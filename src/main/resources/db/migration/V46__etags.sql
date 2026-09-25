-- normalization moved out of this process and onto a queue, and a queue is at-least-once:
-- a reply can be delivered twice, and the api will replay the completion. almost
-- everything downstream of that already converges -- the same UPDATE with the same
-- values, the same S3 copy -- but one thing did not. a replayed MediaNormalizedEvent
-- re-triggered transcription, which is a full ffmpeg decode of the audio plus the model
-- calls, for a transcript the system already had.
--
-- the guard can't be "is it already transcribed", because that is also true in the case
-- that *must* re-transcribe: the mogul replaced the audio. the only thing that separates
-- "the same work again" from "new work" is whether the bytes changed, so the transcript
-- has to record which bytes it was made from.
--
-- S3 hands us that identifier for free. every write already asks for the object's size,
-- and the same HEAD carries the ETag.

alter table managed_file
    add column if not exists etag text null;

-- which version of its source audio this transcript is of. null on every existing row:
-- we don't know what those were made from, and guessing would be worse than
-- re-transcribing once on the next invalidation.
alter table transcript
    add column if not exists source_etag text null;
