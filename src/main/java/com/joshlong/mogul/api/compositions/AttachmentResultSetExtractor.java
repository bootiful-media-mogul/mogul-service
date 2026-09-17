package com.joshlong.mogul.api.compositions;

import com.joshlong.mogul.api.managedfiles.ManagedFile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;

/**
 * reads a result set of {@code composition_attachment} rows and resolves every one of
 * their managed files in a single call, grouped by the composition each belongs to.
 * <p>
 * this is the only way an {@link Attachment} gets built. doing it a row at a time -- the
 * obvious {@link org.springframework.jdbc.core.RowMapper} shape -- means a query per
 * attachment, which is how it used to work: the attachments of a composition were read
 * together in one query and then paid for one by one.
 */
class AttachmentResultSetExtractor implements ResultSetExtractor<Map<Long, List<Attachment>>> {

	private final Function<Collection<Long>, Map<Long, ManagedFile>> managedFiles;

	AttachmentResultSetExtractor(Function<Collection<Long>, Map<Long, ManagedFile>> managedFiles) {
		this.managedFiles = managedFiles;
	}

	@Override
	public Map<Long, List<Attachment>> extractData(ResultSet rs) throws SQLException, DataAccessException {
		var rows = new ArrayList<AttachmentRow>();
		var managedFileIds = new HashSet<Long>();
		while (rs.next()) {
			var row = new AttachmentRow(rs.getLong("composition_id"), rs.getLong("id"), rs.getString("caption"),
					rs.getLong("managed_file_id"));
			rows.add(row);
			managedFileIds.add(row.managedFileId());
		}
		var managedFilesById = this.managedFiles.apply(managedFileIds);
		var results = new LinkedHashMap<Long, List<Attachment>>();
		for (var row : rows)
			results.computeIfAbsent(row.compositionId(), _ -> new ArrayList<>())
				.add(new Attachment(row.id(), row.caption(), managedFilesById.get(row.managedFileId())));
		return results;
	}

	/**
	 * one row, read but not yet resolved: the managed file it points at is fetched for
	 * the whole batch once every row is in hand.
	 */
	private record AttachmentRow(Long compositionId, Long id, String caption, Long managedFileId) {
	}

}
