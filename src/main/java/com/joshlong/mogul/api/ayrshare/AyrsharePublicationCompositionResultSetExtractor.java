package com.joshlong.mogul.api.ayrshare;

import com.joshlong.mogul.api.Publication;
import com.joshlong.mogul.api.compositions.Composition;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;

class AyrsharePublicationCompositionResultSetExtractor
		implements ResultSetExtractor<HashSet<AyrsharePublicationComposition>> {

	private final Function<Collection<Long>, Map<Long, Composition>> collectionOfCompositionsFunction;

	private final Function<Collection<Long>, Map<Long, Publication>> collectionOfPublicationsFunction;

	private final Function<String, Platform> platforms;

	AyrsharePublicationCompositionResultSetExtractor(
			Function<Collection<Long>, Map<Long, Composition>> compositionServiceFunction,
			Function<Collection<Long>, Map<Long, Publication>> collectionOfPublicationsFunction,
			Function<String, Platform> platforms) {
		this.collectionOfCompositionsFunction = compositionServiceFunction;
		this.collectionOfPublicationsFunction = collectionOfPublicationsFunction;
		this.platforms = platforms;
	}

	@Override
	public HashSet<AyrsharePublicationComposition> extractData(ResultSet rs) throws SQLException, DataAccessException {
		var apcToComposition = new HashMap<Long, Long>();
		var publicationToComposition = new HashMap<Long, Long>();
		var ayrsharePublicationCompositions = new ArrayList<AyrsharePublicationComposition>();
		while (rs.next()) {
			var apcId = rs.getLong("id");
			apcToComposition.put(apcId, rs.getLong("composition_id"));
			publicationToComposition.put(apcId, rs.getLong("publication_id"));
			ayrsharePublicationCompositions.add(this.getRootAyrsharePublicationCompositionAndNothingElse(rs));
		}
		var compositionsByIds = collectionOfCompositionsFunction.apply(apcToComposition.values());
		var publicationsByIds = collectionOfPublicationsFunction.apply(publicationToComposition.values());
		var finalList = new LinkedHashSet<AyrsharePublicationComposition>();
		for (var apc : ayrsharePublicationCompositions) {
			var apcId = apc.id();
			var compId = apcToComposition.get(apcId);
			var composition = compositionsByIds.get(compId);
			var publication = publicationsByIds.get(apc.id());
			var apcFinal = new AyrsharePublicationComposition(apc.id(), apc.draft(), publication, apc.platform(),
					composition);
			finalList.add(apcFinal);
		}
		return finalList;
	}

	private AyrsharePublicationComposition getRootAyrsharePublicationCompositionAndNothingElse(ResultSet rs)
			throws SQLException {
		return new AyrsharePublicationComposition(rs.getLong("id"), rs.getBoolean("draft"), null,
				platforms.apply(rs.getString("platform")), null);
	}

}
