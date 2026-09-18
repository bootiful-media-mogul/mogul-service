package com.joshlong.mogul.api.mogul;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * the moguls were cached in plain node-local maps, so an edit on one node stayed stale on
 * every other until the entry aged out -- and {@code today()} reads the mogul's time
 * zone, so that surfaced as dates rendering differently depending on which pod answered.
 * they come from the broadcasting cache manager now.
 * <p>
 * each test makes its own mogul and deletes it again; the suite runs against a shared
 * database.
 */
@SpringBootTest
class MogulCacheTest {

	private static final String INSERT = """
			insert into mogul(username, client_id, email, given_name, family_name, updated)
			values (?, ?, ?, ?, ?, NOW())
			""";

	@Test
	void theMogulCachesComeFromTheBroadcastingManager(@Autowired CacheManager cacheManager) {
		// an eviction only reaches the other nodes if these came from the broadcasting
		// manager. the type is package-private, so check it by name.
		assertThat(cacheManager.getClass().getSimpleName()).isEqualTo("BroadcastingCacheManager");
		assertThat(cacheManager.getCache("mogulsById")).isNotNull();
		assertThat(cacheManager.getCache("mogulsByName")).isNotNull();
	}

	/**
	 * the regression guard. the old {@code computeIfAbsent} never stored a null, but a
	 * cache will keep one happily -- and {@code login} inserts a mogul then reads it
	 * straight back through {@code getMogulByName}. if a miss were cached, that read
	 * would return the cached absence and every first-time login would fail.
	 */
	@Test
	void anAbsentMogulIsNotCachedSoALaterInsertIsVisible(@Autowired MogulService moguls, @Autowired JdbcClient db) {
		var username = "cache-test-" + UUID.randomUUID();
		try {
			assertThat(moguls.getMogulByName(username)).as("nobody by that name yet").isNull();
			db.sql(INSERT).params(username, "cache-test-client", username + "@example.com", "Cache", "Test").update();
			assertThat(moguls.getMogulByName(username))
				.as("the miss must not have been cached, or login() could never see its own insert")
				.isNotNull();
		}
		finally {
			db.sql("delete from mogul where username = ?").param(username).update();
		}
	}

	@Test
	void aMogulIsCachedUnderBothKeys(@Autowired MogulService moguls, @Autowired JdbcClient db,
			@Autowired CacheManager cacheManager) {
		var username = "cache-test-" + UUID.randomUUID();
		try {
			db.sql(INSERT).params(username, "cache-test-client", username + "@example.com", "Cache", "Test").update();
			var mogul = moguls.getMogulByName(username);
			assertThat(mogul).isNotNull();
			assertThat(cacheManager.getCache("mogulsByName").get(username)).as("cached by name").isNotNull();
			moguls.getMogulById(mogul.id());
			assertThat(cacheManager.getCache("mogulsById").get(mogul.id())).as("cached by id").isNotNull();
		}
		finally {
			db.sql("delete from mogul where username = ?").param(username).update();
			cacheManager.getCache("mogulsByName").evict(username);
		}
	}

}
