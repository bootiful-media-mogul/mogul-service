package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.managedfiles.ManagedFile;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Function;

class EpisodeSegmentRowMapper implements RowMapper<Segment> {

	private final Function<Long, ManagedFile> managedFileFunction;

	private final Function<Long, Episode> episodeFunction;

	EpisodeSegmentRowMapper(Function<Long, ManagedFile> managedFileFunction, Function<Long, Episode> episodeFunction) {
		this.managedFileFunction = managedFileFunction;
		this.episodeFunction = episodeFunction;
	}

	@Override
	public Segment mapRow(ResultSet rs, int rowNum) throws SQLException {
		var episode = episodeFunction.apply(rs.getLong("podcast_episode_id"));
		return new Segment(episode, rs.getLong("id"),
				managedFileFunction.apply(rs.getLong("segment_audio_managed_file_id")),
				managedFileFunction.apply(rs.getLong("produced_segment_audio_managed_file_id")),
				rs.getLong("cross_fade_duration"), rs.getString("name"), rs.getInt("sequence_number"));
	}

}