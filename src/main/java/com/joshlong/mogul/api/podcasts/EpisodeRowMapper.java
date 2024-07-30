package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.managedfiles.ManagedFile;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Function;

class EpisodeRowMapper implements RowMapper<Episode> {

	private final Function<Long, Podcast> podcastFunction;

	private final Function<Long, ManagedFile> managedFileFunction;

	EpisodeRowMapper(Function<Long, Podcast> podcastFunction, Function<Long, ManagedFile> managedFileFunction) {
		this.podcastFunction = podcastFunction;
		this.managedFileFunction = managedFileFunction;
	}

	@Override
	public Episode mapRow(ResultSet resultSet, int rowNum) throws SQLException {
		var episodeId = resultSet.getLong("id");
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
