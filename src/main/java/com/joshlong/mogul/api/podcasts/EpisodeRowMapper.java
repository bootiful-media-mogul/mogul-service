package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.managedfiles.ManagedFile;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

class EpisodeRowMapper implements RowMapper<Episode> {

	private final Function<Collection<Long>, Map<Long, ManagedFile>> managedFileFunction;

	private final boolean deep;

	EpisodeRowMapper(boolean deep, Function<Collection<Long>, Map<Long, ManagedFile>> managedFileFunction) {
		this.managedFileFunction = managedFileFunction;
		this.deep = deep;
	}

	@Override
	public Episode mapRow(ResultSet resultSet, int rowNum) throws SQLException {
		var episodeId = resultSet.getLong("id");

		var graphicId = resultSet.getLong("graphic");
		var producedGraphicId = resultSet.getLong("produced_graphic");
		var producedAudioId = resultSet.getLong("produced_audio");
		var list = Set.of(graphicId, producedAudioId, producedGraphicId);
		var all = deep ? managedFileFunction.apply(list) : Map.<Long, ManagedFile>of();
		var graphic = all.get(graphicId);
		var producedGraphic = all.get(producedGraphicId);
		var producedAudio = all.get(producedAudioId);

		return new Episode(//
				episodeId, //
				resultSet.getLong("podcast"), //
				resultSet.getString("title"), //
				resultSet.getString("description"), //
				resultSet.getTimestamp("created"), //
				graphic, //
				producedGraphic, //
				producedAudio, //
				resultSet.getBoolean("complete"), //
				resultSet.getTimestamp("produced_audio_updated"), //
				resultSet.getTimestamp("produced_audio_assets_updated") // ,
		);
	}

}
