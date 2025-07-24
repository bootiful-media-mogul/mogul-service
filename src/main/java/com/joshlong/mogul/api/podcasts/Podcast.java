package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.compositions.Composable;

import java.util.Date;
import java.util.List;

public record Podcast(Long mogulId, Long id, String title, Date created) implements Composable {

	@Override
	public Long compositionKey() {
		return this.id();
	}
}
