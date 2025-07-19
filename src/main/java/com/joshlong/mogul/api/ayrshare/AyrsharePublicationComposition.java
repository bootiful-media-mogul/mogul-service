package com.joshlong.mogul.api.ayrshare;

import com.joshlong.mogul.api.Publication;
import com.joshlong.mogul.api.compositions.Composition;

public record AyrsharePublicationComposition(Long id, boolean draft, Publication publication, Platform platform,
		Composition composition) {
}
