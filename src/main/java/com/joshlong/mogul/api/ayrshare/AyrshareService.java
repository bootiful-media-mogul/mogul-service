package com.joshlong.mogul.api.ayrshare;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface AyrshareService {

	Platform[] platforms();

	Response post(String post, Platform[] platforms, Supplier<String> ayrshareApiKeySupplier,
			Consumer<PostContext> contextConsumer);

	Platform platform(String platformCode);

	Collection<AyrsharePublicationComposition> getDraftAyrsharePublicationCompositionsFor(Long mogulId);

}
