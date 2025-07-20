package com.joshlong.mogul.api.ayrshare;

import java.util.Collection;
import java.util.function.Consumer;

public interface AyrshareService {

	Platform[] platforms();

	Response post(String post, Platform[] platforms, Consumer<PostContext> contextConsumer);

	default Response post(String post, Platform[] platforms) {
		return this.post(post, platforms, null);
	}

	Platform platform(String platformCode);

	Collection<AyrsharePublicationComposition> getDraftAyrsharePublicationCompositionsFor(Long mogulId);

}
