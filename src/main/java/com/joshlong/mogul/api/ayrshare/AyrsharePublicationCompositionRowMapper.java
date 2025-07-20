package com.joshlong.mogul.api.ayrshare;

import com.joshlong.mogul.api.Publication;
import com.joshlong.mogul.api.compositions.Composition;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Function;

class AyrsharePublicationCompositionRowMapper implements RowMapper<AyrsharePublicationComposition> {

	private final Function<Long, Composition> compositions;

	private final Function<Long, Publication> publications;

	private final Function<String, Platform> platforms;

	AyrsharePublicationCompositionRowMapper(Function<Long, Composition> compositions,
			Function<Long, Publication> publications, Function<String, Platform> platforms) {
		this.compositions = compositions;
		this.platforms = platforms;
		this.publications = publications;
	}

	@Override
	public AyrsharePublicationComposition mapRow(ResultSet rs, int rowNum) throws SQLException {
		var publicationId = rs.getLong("publication_id");
		var compositionId = rs.getLong("composition_id");
		var publication = publicationId == 0 ? null : publications.apply(publicationId);
		var composition = compositionId == 0 ? null : compositions.apply(compositionId);
		var platform = platforms.apply(rs.getString("platform"));
		var draft = rs.getBoolean("draft");
		return new AyrsharePublicationComposition(rs.getLong("id"), draft, publication, platform, composition);
	}

}
