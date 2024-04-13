package com.joshlong.mogul.api.podcasts.production;

import com.joshlong.mogul.api.managedfiles.ManagedFile;
import com.joshlong.mogul.api.podcasts.Episode;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.MessagingGateway;

/**
 * given a {@link com.joshlong.mogul.api.podcasts.Podcast}, turn this into a complete
 * audio file
 */
@MessagingGateway
public interface PodcastProducer {

	@Gateway(requestChannel = ProductionIntegrationFlowConfiguration.PRODUCTION_FLOW_REQUESTS)
	ManagedFile produce(Episode episode);

}
