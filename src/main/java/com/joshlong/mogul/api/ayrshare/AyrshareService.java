package com.joshlong.mogul.api.ayrshare;

import java.util.*;
import java.util.function.Consumer;

/**
 * we will implement what's described
 * <a href="https://github.com/bootiful-media-mogul/mogul-service/issues/101">here</a>.
 */
public interface AyrshareService {

	Platform[] platforms();

	Response post(String post, Platform[] platforms, Consumer<PostContext> contextConsumer);

	default Response post(String post, Platform[] platforms) {
		return this.post(post, platforms, null);
	}

	Platform platform(String platformCode);

	Collection<AyrsharePublicationComposition> getDraftAyrsharePublicationCompositionsFor(Long mogulId);

}
