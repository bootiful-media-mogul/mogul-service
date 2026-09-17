package com.joshlong.mogul.api.managedfiles;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * the managed file cache exists to keep the callers that ask for the same files over and
 * over off the database. it only does that if a wholly cached lookup asks nothing.
 */
@SpringBootTest
class ManagedFileCachingTest {

	@MockitoSpyBean
	private JdbcClient db;

	@Test
	void aFullyCachedLookupAsksTheDatabaseNothing(@Autowired ManagedFileService managedFileService) {
		var ids = new ArrayList<Long>();
		try {
			var mogulId = this.db.sql("select id from mogul").query((rs, _) -> rs.getLong("id")).list().getFirst();
			for (var i = 0; i < 3; i++)
				ids.add(this.managedFile(mogulId));

			// the first lookup is a cache miss, and is allowed its one query.
			var first = managedFileService.getManagedFiles(ids);
			assertEquals(ids.size(), first.size(), "every file should come back");

			Mockito.clearInvocations(this.db);
			var second = managedFileService.getManagedFiles(ids);
			assertEquals(first.keySet(), second.keySet(), "the cached lookup returns the same files");
			verify(this.db, never()).sql(anyString());
		} //
		finally {
			for (var id : ids)
				this.db.sql("delete from managed_file where id = ?").params(id).update();
		}
	}

	/**
	 * a nullable managed file column arrives here as 0. there is no such row, so there is
	 * no reason to go and ask for it -- and asking would also mean the lookup was never
	 * wholly cached, so it would drag a query behind it forever.
	 */
	@Test
	void anAbsentManagedFileIsNotLookedUp(@Autowired ManagedFileService managedFileService) {
		Mockito.clearInvocations(this.db);
		var found = managedFileService.getManagedFiles(List.of(0L));
		assertTrue(found.isEmpty(), "there is no managed file 0");
		verify(this.db, never()).sql(anyString());
	}

	private Long managedFile(Long mogulId) {
		return this.db
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
