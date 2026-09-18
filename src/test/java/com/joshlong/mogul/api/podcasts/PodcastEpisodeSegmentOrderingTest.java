package com.joshlong.mogul.api.podcasts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PodcastEpisodeSegmentOrderingTest {

	private final int writersCount = 4;

	@Test
	void concurrentSegmentCreatesNeverShareASequenceNumber(@Autowired JdbcClient db,
			@Autowired PodcastService podcastService) throws Exception {
		var mogulId = db.sql("select id from mogul").query((rs, _) -> rs.getLong("id")).list().getFirst();
		// a podcast and episode of its own: the assertions below count segments exactly.
		var podcastId = db.sql("insert into podcast(mogul_id, title) values (?,?) returning id")
			.params(mogulId, "segment ordering test " + UUID.randomUUID())
			.query((rs, _) -> rs.getLong("id"))
			.single();
		// all three managed files: refreshing an episode's completeness reads the
		// produced audio, so an episode without one never gets as far as the race.
		var graphicId = this.managedFile(db, mogulId);
		var producedGraphicId = this.managedFile(db, mogulId);
		var producedAudioId = this.managedFile(db, mogulId);
		var episodeId = db.sql("""
				insert into podcast_episode(podcast_id, title, description, graphic_managed_file_id,
				    produced_graphic_managed_file_id, produced_audio_managed_file_id)
				values (?,?,?,?,?,?) returning id
				""")
			.params(podcastId, "ordering", "description", graphicId, producedGraphicId, producedAudioId)
			.query((rs, _) -> rs.getLong("id"))
			.single();
		try {
			// every writer released at once, so they genuinely overlap rather than
			// queueing up behind each other's start-up.
			var start = new CountDownLatch(1);
			var failures = new ArrayList<Throwable>();
			try (var executor = Executors.newFixedThreadPool(writersCount)) {
				var done = new CountDownLatch(writersCount);
				for (var i = 0; i < writersCount; i++) {
					var which = i;
					executor.submit(() -> {
						try {
							start.await();
							podcastService.createPodcastEpisodeSegment(mogulId, episodeId, "segment " + which, 0);
						}
						catch (Throwable t) {
							synchronized (failures) {
								failures.add(t);
							}
						}
						finally {
							done.countDown();
						}
					});
				}
				start.countDown();
				assertTrue(done.await(60, TimeUnit.SECONDS), "the writers should all finish");
			}

			assertEquals(List.of(), failures, "no writer should have failed");

			var sequenceNumbers = db
				.sql("select sequence_number from podcast_episode_segment where podcast_episode_id = ?")
				.params(episodeId)
				.query((rs, _) -> rs.getInt("sequence_number"))
				.list();
			assertEquals(writersCount, sequenceNumbers.size(), "every segment should have been created");
			assertEquals(writersCount, new HashSet<>(sequenceNumbers).size(),
					"every segment should have its own sequence number, got " + sequenceNumbers);

			// and the read agrees with the schema: n segments, numbered 1..n in order.
			var ordered = podcastService.getPodcastEpisodeSegmentsByEpisode(episodeId);
			assertEquals(writersCount, ordered.size(), "every segment should read back");
			for (var i = 0; i < ordered.size(); i++)
				assertEquals(i + 1, ordered.get(i).order(), "the segments should read back in order");
		} //
		finally {
			var managedFileIds = db.sql("""
					select segment_audio_managed_file_id a, produced_segment_audio_managed_file_id b
					from podcast_episode_segment where podcast_episode_id = ?
					""").params(episodeId).query((rs, _) -> List.of(rs.getLong("a"), rs.getLong("b"))).list();
			db.sql("delete from podcast_episode_segment where podcast_episode_id = ?").params(episodeId).update();
			for (var pair : managedFileIds)
				for (var managedFileId : pair)
					db.sql("delete from managed_file where id = ?").params(managedFileId).update();
			db.sql("delete from podcast_episode where id = ?").params(episodeId).update();
			for (var managedFileId : List.of(graphicId, producedGraphicId, producedAudioId))
				db.sql("delete from managed_file where id = ?").params(managedFileId).update();
			db.sql("delete from podcast where id = ?").params(podcastId).update();
		}
	}

	/**
	 * reordering renumbers the segments one update at a time, so partway through the loop
	 * two rows genuinely do hold the same sequence number. that is why the constraint is
	 * deferred: checked per-statement it would refuse every move, and the invariant that
	 * actually matters is that the duplicate is gone by the time the transaction commits.
	 */
	@Test
	void movingASegmentRenumbersThroughATransientDuplicate(@Autowired JdbcClient db,
			@Autowired PodcastService podcastService) {
		var mogulId = db.sql("select id from mogul").query((rs, _) -> rs.getLong("id")).list().getFirst();
		var podcastId = db.sql("insert into podcast(mogul_id, title) values (?,?) returning id")
			.params(mogulId, "segment move test " + UUID.randomUUID())
			.query((rs, _) -> rs.getLong("id"))
			.single();
		var graphicId = this.managedFile(db, mogulId);
		var producedGraphicId = this.managedFile(db, mogulId);
		var producedAudioId = this.managedFile(db, mogulId);
		var episodeId = db.sql("""
				insert into podcast_episode(podcast_id, title, description, graphic_managed_file_id,
				    produced_graphic_managed_file_id, produced_audio_managed_file_id)
				values (?,?,?,?,?,?) returning id
				""")
			.params(podcastId, "moving", "description", graphicId, producedGraphicId, producedAudioId)
			.query((rs, _) -> rs.getLong("id"))
			.single();
		try {
			for (var i = 0; i < 3; i++)
				podcastService.createPodcastEpisodeSegment(mogulId, episodeId, "segment " + i, 0);
			var before = podcastService.getPodcastEpisodeSegmentsByEpisode(episodeId);
			assertEquals(3, before.size(), "three segments to shuffle");
			var last = before.get(2);

			// 1,2,3 -> the last one moves up, and midway through the renumbering two rows
			// both hold 2.
			podcastService.movePodcastEpisodeSegmentUp(episodeId, last.id());

			var after = podcastService.getPodcastEpisodeSegmentsByEpisode(episodeId);
			assertEquals(List.of(1, 2, 3), after.stream().map(Segment::order).toList(),
					"the segments are still numbered 1..n");
			assertEquals(last.id(), after.get(1).id(), "the moved segment sits one place earlier");
		} //
		finally {
			var managedFileIds = db.sql("""
					select segment_audio_managed_file_id a, produced_segment_audio_managed_file_id b
					from podcast_episode_segment where podcast_episode_id = ?
					""").params(episodeId).query((rs, _) -> List.of(rs.getLong("a"), rs.getLong("b"))).list();
			db.sql("delete from podcast_episode_segment where podcast_episode_id = ?").params(episodeId).update();
			for (var pair : managedFileIds)
				for (var managedFileId : pair)
					db.sql("delete from managed_file where id = ?").params(managedFileId).update();
			db.sql("delete from podcast_episode where id = ?").params(episodeId).update();
			for (var managedFileId : List.of(graphicId, producedGraphicId, producedAudioId))
				db.sql("delete from managed_file where id = ?").params(managedFileId).update();
			db.sql("delete from podcast where id = ?").params(podcastId).update();
		}
	}

	private Long managedFile(JdbcClient db, Long mogulId) {
		return db
			.sql("""
					insert into managed_file(storage_filename, mogul_id, bucket, folder, filename, size, content_type, visible)
					values (?,?,?,?,?,?,?,?) returning id
					""")
			.params(UUID.randomUUID().toString(), mogulId, "test-bucket", "test-folder", "test.bin", 0,
					"application/octet-stream", false)
			.query((rs, _) -> rs.getLong("id"))
			.single();
	}

}
