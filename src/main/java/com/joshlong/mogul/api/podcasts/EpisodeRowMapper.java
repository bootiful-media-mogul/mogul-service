package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.managedfiles.ManagedFile;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Function;

class EpisodeRowMapper implements RowMapper<Episode> {

	private final Function<Long, ManagedFile> managedFileFunction;

	private final boolean deep;

	EpisodeRowMapper(boolean deep, Function<Long, ManagedFile> managedFileFunction) {
		this.managedFileFunction = managedFileFunction;
		this.deep = deep;
	}

	@Override
	public Episode mapRow(ResultSet resultSet, int rowNum) throws SQLException {
		var episodeId = resultSet.getLong("id");
		return new Episode(//
				episodeId, //
				resultSet.getLong("podcast"), //
				resultSet.getString("title"), //
				resultSet.getString("description"), //
				resultSet.getTimestamp("created"), //
				this.managedFileFunction.apply(resultSet.getLong("graphic")), //
				this.managedFileFunction.apply(resultSet.getLong("produced_graphic")), //
				this.managedFileFunction.apply(resultSet.getLong("produced_audio")), //
				resultSet.getBoolean("complete"), //
				resultSet.getTimestamp("produced_audio_updated"), //
				resultSet.getTimestamp("produced_audio_assets_updated") // ,
		);
	}

}
