package com.joshlong.mogul.api.compositions;

import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * a composition's attachments are read in one query. their managed files have to be read
 * in one query too, or the batching above them buys nothing.
 */
@SpringBootTest
class CompositionBatchLoadingTest {

	private static final int ATTACHMENTS = 4;

	@MockitoSpyBean
	private ManagedFileService managedFileService;

	@Test
	void loadingACompositionResolvesEveryAttachmentsManagedFileInOneCall(@Autowired JdbcClient db,
			@Autowired CompositionService compositionService) {
		var mogulId = db.sql("select id from mogul").query((rs, _) -> rs.getLong("id")).list().getFirst();
		var managedFileIds = new ArrayList<Long>();
		var compositionId = (Long) null;
		try {
			compositionId = db.sql("""
					insert into composition(payload_class, payload, field) values (?,?,?) returning id
					""")
				.params(CompositionBatchLoadingTest.class.getName(), UUID.randomUUID().toString(), "test")
				.query((rs, _) -> rs.getLong("id"))
				.single();
			for (var i = 0; i < ATTACHMENTS; i++) {
				var managedFileId = this.managedFile(db, mogulId);
				managedFileIds.add(managedFileId);
				db.sql("insert into composition_attachment(composition_id, managed_file_id, caption) values (?,?,?)")
					.params(compositionId, managedFileId, "caption " + i)
					.update();
			}

			Mockito.clearInvocations(this.managedFileService);
			var composition = compositionService.getCompositionById(compositionId);

			assertNotNull(composition, "the composition should load");
			assertEquals(ATTACHMENTS, composition.attachments().size(), "every attachment should come back");
			for (var attachment : composition.attachments())
				assertNotNull(attachment.managedFile(), "every attachment's managed file should be resolved");
			// the point of the test: one call for all of them, not one per attachment.
			verify(this.managedFileService, times(1)).getManagedFiles(any());
		} //
		finally {
			if (compositionId != null) {
				db.sql("delete from composition_attachment where composition_id = ?").params(compositionId).update();
				db.sql("delete from composition where id = ?").params(compositionId).update();
			}
			for (var id : managedFileIds)
				db.sql("delete from managed_file where id = ?").params(id).update();
		}
	}

	/**
	 * the single-attachment read goes through the same extractor, which is the point of
	 * there being only one: {@code readThroughAttachmentById} issues exactly this query
	 * and flattens the one group it gets back.
	 */
	@Test
	void theSameExtractorResolvesASingleAttachment(@Autowired JdbcClient db) {
		var mogulId = db.sql("select id from mogul").query((rs, _) -> rs.getLong("id")).list().getFirst();
		var managedFileId = this.managedFile(db, mogulId);
		var attachmentId = db.sql(
				"insert into composition_attachment(composition_id, managed_file_id, caption) values (?,?,?) returning id")
			.params(null, managedFileId, "lonely")
			.query((rs, _) -> rs.getLong("id"))
			.single();
		try {
			Mockito.clearInvocations(this.managedFileService);
			var found = db.sql("select * from composition_attachment where id = ?")
				.param(attachmentId)
				.query(new AttachmentResultSetExtractor(this.managedFileService::getManagedFiles))
				.values()
				.stream()
				.flatMap(Collection::stream)
				.toList();
			assertEquals(1, found.size(), "exactly one attachment comes back");
			assertEquals("lonely", found.getFirst().caption());
			assertEquals(managedFileId, found.getFirst().managedFile().id(), "its managed file is resolved");
			verify(this.managedFileService, times(1)).getManagedFiles(any());
		} //
		finally {
			db.sql("delete from composition_attachment where id = ?").params(attachmentId).update();
			db.sql("delete from managed_file where id = ?").params(managedFileId).update();
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
