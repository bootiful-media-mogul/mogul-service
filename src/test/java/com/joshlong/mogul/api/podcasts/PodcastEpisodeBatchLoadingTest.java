package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * a deep episode load resolves three managed files per episode. it must resolve all of
 * them in one trip, not one trip per episode: the feed and the episode list both ask for
 * every episode of a podcast at once, and a query per row is a query per row no matter
 * how warm the managed file cache is.
 */
@SpringBootTest
class PodcastEpisodeBatchLoadingTest {

	private static final int EPISODES = 4;

	@MockitoSpyBean
	private ManagedFileService managedFileService;

	@Test
	void deepLoadResolvesEveryEpisodesManagedFilesInOneCall(@Autowired JdbcClient db,
			@Autowired PodcastService podcastService) {
		var mogulId = db.sql("select id from mogul").query((rs, _) -> rs.getLong("id")).list().getFirst();
		// a podcast of its own, and not whichever one happens to be first: the counts
		// below are exact, so a single episode belonging to someone else -- left behind
		// by another test, or sitting in the developer's own database -- would fail this
		// for the wrong reason.
		var podcastId = db.sql("insert into podcast(mogul_id, title) values (?,?) returning id")
			.params(mogulId, "batch loading test " + UUID.randomUUID())
			.query((rs, _) -> rs.getLong("id"))
			.single();
		var episodeIds = new ArrayList<Long>();
		var managedFileIds = new ArrayList<Long>();
		try {
			for (var i = 0; i < EPISODES; i++) {
				var graphic = this.managedFile(db, mogulId);
				var producedGraphic = this.managedFile(db, mogulId);
				var producedAudio = this.managedFile(db, mogulId);
				managedFileIds.add(graphic);
				managedFileIds.add(producedGraphic);
				managedFileIds.add(producedAudio);
				episodeIds.add(db.sql("""
						insert into podcast_episode(podcast_id, title, description, graphic_managed_file_id,
						    produced_graphic_managed_file_id, produced_audio_managed_file_id)
						values (?,?,?,?,?,?) returning id
						""")
					.params(podcastId, "episode " + i, "description " + i, graphic, producedGraphic, producedAudio)
					.query((rs, _) -> rs.getLong("id"))
					.single());
			}

			Mockito.clearInvocations(this.managedFileService);
			var deep = podcastService.getPodcastEpisodesByPodcast(podcastId, true);

			assertEquals(EPISODES, deep.size(), "every episode should come back");
			for (var episode : deep) {
				assertNotNull(episode.graphic(), "a deep load resolves the graphic");
				assertNotNull(episode.producedGraphic(), "a deep load resolves the produced graphic");
				assertNotNull(episode.producedAudio(), "a deep load resolves the produced audio");
			}
			// the point of the test: one call for all of them, not one per episode.
			verify(this.managedFileService, times(1)).getManagedFiles(any());

			Mockito.clearInvocations(this.managedFileService);
			var shallow = podcastService.getPodcastEpisodesByPodcast(podcastId, false);
			assertEquals(EPISODES, shallow.size(), "a shallow load returns the same episodes");
			for (var episode : shallow)
				assertNull(episode.graphic(), "a shallow load resolves no managed files at all");
			verify(this.managedFileService, never()).getManagedFiles(any());
		} //
		finally {
			for (var episodeId : episodeIds)
				db.sql("delete from podcast_episode where id = ?").params(episodeId).update();
			for (var managedFileId : managedFileIds)
				db.sql("delete from managed_file where id = ?").params(managedFileId).update();
			db.sql("delete from podcast where id = ?").params(podcastId).update();
		}
	}

	/**
	 * the produced graphic and produced audio columns are both nullable, and a null one
	 * arrives here as 0. two of them therefore arrive as the same value, which is fatal
	 * to a set that refuses duplicates -- and it was fatal to the whole query, not just
	 * to the one episode, since the row mapper threw partway through the result set.
	 */
	@Test
	void anEpisodeWithNeitherProducedFileStillLoads(@Autowired JdbcClient db,
			@Autowired PodcastService podcastService) {
		var mogulId = db.sql("select id from mogul").query((rs, _) -> rs.getLong("id")).list().getFirst();
		var podcastId = db.sql("insert into podcast(mogul_id, title) values (?,?) returning id")
			.params(mogulId, "unproduced episode test " + UUID.randomUUID())
			.query((rs, _) -> rs.getLong("id"))
			.single();
		var graphicId = this.managedFile(db, mogulId);
		var episodeId = db.sql("""
				insert into podcast_episode(podcast_id, title, description, graphic_managed_file_id)
				values (?,?,?,?) returning id
				""")
			.params(podcastId, "not produced yet", "description", graphicId)
			.query((rs, _) -> rs.getLong("id"))
			.single();
		try {
			for (var deep : new boolean[] { false, true }) {
				var episodes = podcastService.getPodcastEpisodesByPodcast(podcastId, deep);
				assertEquals(1, episodes.size(), "the episode should load (deep? " + deep + ")");
				var episode = episodes.iterator().next();
				assertNull(episode.producedGraphic(), "there is no produced graphic to resolve");
				assertNull(episode.producedAudio(), "there is no produced audio to resolve");
			}
			// the graphic is not null, so a deep read still resolves the one file there
			// is.
			var episode = podcastService.getPodcastEpisodesByPodcast(podcastId, true).iterator().next();
			assertNotNull(episode.graphic(), "the graphic is set and should resolve");
		} //
		finally {
			db.sql("delete from podcast_episode where id = ?").params(episodeId).update();
			db.sql("delete from managed_file where id = ?").params(graphicId).update();
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
