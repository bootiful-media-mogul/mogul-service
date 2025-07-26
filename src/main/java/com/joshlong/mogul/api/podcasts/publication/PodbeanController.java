package com.joshlong.mogul.api.podcasts.publication;

import com.joshlong.mogul.api.Publication;
import com.joshlong.mogul.api.utils.CollectionUtils;
import com.joshlong.podbean.Episode;
import com.joshlong.podbean.PodbeanClient;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TODO this will be deleted!
 */
@Controller
@ResponseBody
class PodbeanController {

	private final PodbeanClient podbeanClient;

	private final JdbcClient db;

	private final TransactionTemplate transactionTemplate;

	PodbeanController(PodbeanClient podbeanClient, JdbcClient db, TransactionTemplate transactionTemplate) {
		this.podbeanClient = podbeanClient;
		this.db = db;
		this.transactionTemplate = transactionTemplate;
	}

	@GetMapping("/podbean/retro")
	boolean retro() throws Exception {
		this.doMigrate();
		return true;
	}

	private void doMigrate() throws Exception {
		var findPodcastEpisodesWithoutPublicationOutcomes = """
				   SELECT pp.id as publication_id, cast(pp.payload as int) as podcast_episode_id
				      FROM publication pp
				      WHERE id IN (select po.id
				                   from publication_outcome po
				                   where po.key = 'podbean'
				                     and not exists(select 1
				                                    from publication_outcome po2
				                                    where po2.publication_id = po.publication_id
				                                      and po2.uri is not null));
				""";
		var podcastEpisodesToPublications = new HashMap<Long, Long>();
		db.sql(findPodcastEpisodesWithoutPublicationOutcomes)
			.query((rs, _) -> Map.entry(rs.getLong("podcast_episode_id"), rs.getLong("publication_id")))
			.stream()
			.forEach(entry -> podcastEpisodesToPublications.put(entry.getKey(), entry.getValue()));
		var podcastEpisodeIdToTitle = db
			.sql("select pe.title, pe.id from podcast_episode pe where pe.id in  (%s)"
				.formatted(CollectionUtils.join(podcastEpisodesToPublications.values(), ",")))
			.query((rs, rowNum) -> Map.entry(rs.getLong("id"), rs.getString("title")))
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		var allPodbeanEpisodes = podbeanClient.getAllEpisodes();
		transactionTemplate.executeWithoutResult(status -> {
			for (var entry : podcastEpisodeIdToTitle.entrySet()) {
				var podcastEpisodeId = entry.getKey();
				var podcastEpisodeTitle = entry.getValue();
				var publicationId = podcastEpisodesToPublications.get(podcastEpisodeId);
				var matches = allPodbeanEpisodes.stream()
					.filter(pe -> pe.getTitle().equals(podcastEpisodeTitle))
					.toList();
				if (!matches.isEmpty()) {
					var first = matches.getFirst();
					var bad = (null == publicationId || null == first || first.getPermalinkUrl() == null);
					if (!bad) {
						System.out.println("updating podcast episode " + podcastEpisodeId + " with publication id "
								+ publicationId + " and title " + podcastEpisodeTitle + " and podbean episode id "
								+ first.getId());
						this.reify(publicationId, first);
					} //
					else {
						System.out.println("can't handle " + podcastEpisodeId + '.');
					}
				}
			}
		});

	}

	private void reify(Long publicationId, Episode podbeanEpisode) {

		this.db.sql(" update publication set state = ? where id = ?")
			.params(Publication.State.PUBLISHED.name(), publicationId)
			.update();
		this.db.sql(
				"insert into publication_outcome(publication_id, success, uri , key ,server_error_message ) values (?,?,?,?,?)")
			.params(publicationId, true, podbeanEpisode.getPermalinkUrl().toString(), "podbean", null)
			.update();
	}

}