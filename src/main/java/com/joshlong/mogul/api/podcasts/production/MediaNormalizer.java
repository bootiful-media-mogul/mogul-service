package com.joshlong.mogul.api.podcasts.production;

import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.MessagingGateway;

@MessagingGateway
public interface MediaNormalizer {

	@SuppressWarnings("UnresolvedMessageChannel")
	@Gateway(requestChannel = MediaNormalizationIntegration.MEDIA_NORMALIZATION_FLOW_CHANNEL)
	MediaNormalizationIntegrationResponse normalize(MediaNormalizationIntegrationRequest request);

}
