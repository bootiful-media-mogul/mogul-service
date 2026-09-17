package com.joshlong.mogul.api.publications;

import com.joshlong.mogul.api.Publication;
import com.joshlong.mogul.api.podcasts.Episode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.util.ArrayList;
import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * looking publications up by id has to work for more than nothing. postgres will not
 * accept a scalar on the right of ANY, so binding the ids as anything but an array threw
 * for every non-empty lookup -- which stayed hidden because the one caller passes
 * ayrshare drafts, whose publication ids are null and are filtered out before the query
 * runs.
 */
@SpringBootTest
class PublicationsByIdsTest {

	@Test
	void severalIdsAtOnce(@Autowired JdbcClient db, @Autowired PublicationService publicationService,
			@Autowired TextEncryptor textEncryptor) {
		var mogulId = db.sql("select id from mogul").query((rs, _) -> rs.getLong("id")).list().getFirst();
		var ids = new ArrayList<Long>();
		try {
			for (var i = 0; i < 2; i++)
				ids.add(this.publication(db, textEncryptor, mogulId, "test-plugin-" + i));

			var found = publicationService.getPublicationsByIds(Set.copyOf(ids));

			assertEquals(ids.size(), found.size(), "both publications should come back");
			for (var id : ids) {
				var publication = found.get(id);
				assertNotNull(publication, "publication " + id + " should be found");
				assertEquals(Publication.State.PUBLISHED, publication.state());
				assertNotNull(publication.outcomes(), "its outcomes should be materialized, even if empty");
			}
			// one id was never the problem, but it is the case the broken version
			// happened to be exercised with, so pin it too.
			assertEquals(1, publicationService.getPublicationsByIds(Set.of(ids.getFirst())).size());
			assertTrue(publicationService.getPublicationsByIds(Set.of()).isEmpty(), "nothing asked, nothing found");
		} //
		finally {
			for (var id : ids)
				db.sql("delete from publication where id = ?").params(id).update();
		}
	}

	private Long publication(JdbcClient db, TextEncryptor textEncryptor, Long mogulId, String plugin) {
		return db.sql("""
				insert into publication(state, mogul_id, plugin, created, published, context, payload, payload_class)
				values (?,?,?,?,?,?,?,?) returning id
				""")
			.params(Publication.State.PUBLISHED.name(), mogulId, plugin, new Date(), new Date(),
					textEncryptor.encrypt("{}"), "1", Episode.class.getName())
			.query((rs, _) -> rs.getLong("id"))
			.single();
	}

}
