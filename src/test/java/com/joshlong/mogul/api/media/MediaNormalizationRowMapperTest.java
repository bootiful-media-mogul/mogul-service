package com.joshlong.mogul.api.media;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * the caller's context is parked as json for however long the processors module takes,
 * and json has no memory of the difference between {@code 2} and {@code 2L}. every
 * listener of {@link MediaNormalizedEvent} casts those ids straight back to {@code Long},
 * so a plain read would hand them an {@link Integer} and a {@link ClassCastException} --
 * and only for ids small enough to fit in one, which is to say in development and not in
 * production.
 */
class MediaNormalizationRowMapperTest {

	@Test
	void theCallersIdsComeBackOutAsLongs() throws Exception {
		var rs = mock(ResultSet.class);
		when(rs.getLong("id")).thenReturn(1L);
		when(rs.getString("correlation_id")).thenReturn("a-correlation-id");
		when(rs.getLong("input_managed_file_id")).thenReturn(10L);
		when(rs.getLong("output_managed_file_id")).thenReturn(11L);
		// DefaultPodcastService's context keys, which are package private over there.
		when(rs.getString("context")).thenReturn("""
				{ "podcastEpisodeId" : 2, "podcastEpisodeSegmentId": 3 }
				""");
		when(rs.getTimestamp(anyString())).thenReturn(Timestamp.from(Instant.now()));
		when(rs.getObject("successful")).thenReturn(null);

		var normalization = new MediaNormalizationRowMapper(JsonMapper.builder().build()).mapRow(rs, 0);

		assertThat(normalization.correlationId()).isEqualTo("a-correlation-id");
		assertThat(normalization.context()).containsEntry("podcastEpisodeId", 2L)
			.containsEntry("podcastEpisodeSegmentId", 3L);
		assertThat(normalization.context().values()).allSatisfy(v -> assertThat(v).isInstanceOf(Long.class));
		assertThat(normalization.successful()).isNull();
	}

}
