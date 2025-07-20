package com.joshlong.mogul.api.ayrshare;

import com.joshlong.mogul.api.Publication;
import com.joshlong.mogul.api.compositions.Composable;
import com.joshlong.mogul.api.compositions.Composition;

public record AyrsharePublicationComposition(Long id, boolean draft, Publication publication, Platform platform,
		Composition composition) implements Composable {

	@Override
	public Long compositionKey() {
		return this.id();
	}
}
