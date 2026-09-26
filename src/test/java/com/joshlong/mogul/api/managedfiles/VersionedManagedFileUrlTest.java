package com.joshlong.mogul.api.managedfiles;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class VersionedManagedFileUrlTest {

	@Test
	void theEtagBecomesACacheBustingQueryParameter(@Autowired JdbcClient db,
			@Autowired ManagedFileService managedFileService) {
		var mogulId = db.sql("select id from mogul").query((rs, _) -> rs.getLong("id")).list().getFirst();
		var created = new ArrayList<Long>();
		try {
			// S3 quotes its etags, and a multipart upload's carries a part count. the
			// quotes would have to be percent-encoded to survive a URL; the rest is hex
			// and a dash, and needs nothing done to it.
			var singlePart = managedFile(db, mogulId, created, "\"aa9a06f3965679fe0ebee704c503d2c5\"", true, true);
			var multiPart = managedFile(db, mogulId, created, "\"b05bb12dda77711921d364d9d3860f20-6\"", true, true);
			var noEtag = managedFile(db, mogulId, created, null, true, true);

			var single = managedFileService.getVersionedPublicUrlForManagedFile(singlePart);
			assertTrue(single.endsWith("?v=aa9a06f3965679fe0ebee704c503d2c5"), single);
			assertFalse(single.contains("\""), "quotes have no business in a URL: " + single);
			assertFalse(single.contains("%22"), "and neither does an encoded quote: " + single);

			var multi = managedFileService.getVersionedPublicUrlForManagedFile(multiPart);
			assertTrue(multi.endsWith("?v=b05bb12dda77711921d364d9d3860f20-6"), multi);

			// a file written before we started recording etags goes unversioned rather
			// than unfetchable
			var unversioned = managedFileService.getVersionedPublicUrlForManagedFile(noEtag);
			assertFalse(unversioned.contains("v="), unversioned);

			var downloadable = managedFileService.getDownloadableUrlForManagedFile(singlePart);
			assertTrue(downloadable.contains("v=aa9a06f3965679fe0ebee704c503d2c5"), downloadable);
			assertTrue(downloadable.contains("download=true"), downloadable);
			assertEquals(1, downloadable.chars().filter(c -> c == '?').count(),
					"the two parameters belong to one query string: " + downloadable);

			// and there is nothing to hand out for a file with no bytes in it, or one
			// that isn't public
			var unwritten = managedFile(db, mogulId, created, "\"abc\"", false, true);
			assertNull(managedFileService.getDownloadableUrlForManagedFile(unwritten));
			var invisible = managedFile(db, mogulId, created, "\"def\"", true, false);
			assertNull(managedFileService.getDownloadableUrlForManagedFile(invisible));
		} //
		finally {
			for (var managedFileId : created)
				db.sql("delete from managed_file where id = ?").params(managedFileId).update();
		}
	}

	private Long managedFile(JdbcClient db, Long mogulId, List<Long> created, String etag, boolean written,
			boolean visible) {
		var id = db.sql("""
				insert into managed_file(mogul_id, bucket, folder, filename, storage_filename, written, visible,
				    content_type, size, etag)
				values (?,?,?,?,?,?,?,?,?,?) returning id
				""")
			.params(mogulId, "bucket", UUID.randomUUID().toString(), "produced-audio.mp3", UUID.randomUUID().toString(),
					written, visible, "audio/mpeg", 100, etag)
			.query((rs, _) -> rs.getLong("id"))
			.single();
		created.add(id);
		return id;
	}

}
