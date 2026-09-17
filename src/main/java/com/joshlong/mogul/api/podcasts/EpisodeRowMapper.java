package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.managedfiles.ManagedFile;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

		var graphicId = resultSet.getLong("graphic_managed_file_id");
		var producedGraphicId = resultSet.getLong("produced_graphic_managed_file_id");
		var producedAudioId = resultSet.getLong("produced_audio_managed_file_id");
		// a nullable produced_* column reads back as 0, and Set.of rejects duplicates, so
		// an episode with neither produced file used to fail the whole query on
		// "duplicate element: 0" -- and it did so before `deep` was even consulted, which
		// took the shallow reads down with it. a HashSet tolerates the repeat, and 0 is
		// not an id worth asking the database about in the first place.
		var ids = new HashSet<Long>();
		for (var id : List.of(graphicId, producedAudioId, producedGraphicId))
			if (id > 0)
				ids.add(id);
		var all = deep ? managedFileFunction.apply(ids) : Map.<Long, ManagedFile>of();
		var graphic = all.getOrDefault(graphicId, null);
		var producedGraphic = all.getOrDefault(producedGraphicId, null);
		var producedAudio = all.getOrDefault(producedAudioId, null);

		return new Episode(//
				episodeId, //
				resultSet.getLong("podcast_id"), //
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
