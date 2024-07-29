package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.Publication;
import com.joshlong.mogul.api.managedfiles.ManagedFile;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.function.Function;

class EpisodeRowMapper implements RowMapper<Episode> {

	private final Log log = LogFactory.getLog(getClass());

	private final Function<Long, Podcast> podcastFunction;

	private final Function<Long, ManagedFile> managedFileFunction;

	// private final Function<Long, Collection<Publication>> publicationsFunction;

	EpisodeRowMapper( // Function<Long, Collection<Publication>> publicationFunction,
			Function<Long, Podcast> podcastFunction, Function<Long, ManagedFile> managedFileFunction) {
		this.podcastFunction = podcastFunction;
		this.managedFileFunction = managedFileFunction;
		// this.publicationsFunction = publicationFunction;
	}

	@Override
	public Episode mapRow(ResultSet resultSet, int rowNum) throws SQLException {
		var episodeId = resultSet.getLong("id");
		// var publications = this.publicationsFunction.apply(episodeId);
		//
		// if (!publications.isEmpty())
		// this.log.debug("publications for episode # " + episodeId + ": [" + publications
		// + "]");

		return new Episode(//
				episodeId, //
				this.podcastFunction.apply(resultSet.getLong("podcast_id")), //
				resultSet.getString("title"), //
				resultSet.getString("description"), //
				resultSet.getDate("created"), //
				this.managedFileFunction.apply(resultSet.getLong("graphic")), //
				this.managedFileFunction.apply(resultSet.getLong("produced_graphic")), //
				this.managedFileFunction.apply(resultSet.getLong("produced_audio")), //
				resultSet.getBoolean("complete"), //
				resultSet.getDate("produced_audio_updated"), //
				resultSet.getDate("produced_audio_assets_updated") //
		);
	}

}
