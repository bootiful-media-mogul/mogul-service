package com.joshlong.mogul.api.managedfiles;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.PollerFactory;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Duration;
import java.util.Collection;

@Configuration
@RegisterReflectionForBinding(ManagedFile.class)
class ManagedFilesConfiguration {

	@Bean
	IntegrationFlow managedFileDeletionRequestsIntegrationFlow(ManagedFileService managedFileService) {

		var messageSource = (MessageSource<Collection<ManagedFileDeletionRequest>>) () -> MessageBuilder
			.withPayload(managedFileService.getOutstandingManagedFileDeletionRequests())
			.build();

		return IntegrationFlow
			.from(messageSource,
					pc -> pc.poller(_ -> PollerFactory.fixedRate(Duration.ofMinutes(10), Duration.ofMinutes(1))))

			.split()
			// this does the dirty work of deleting the bits from s3.
			.handle(ManagedFileDeletionRequest.class, (payload, _) -> {
				managedFileService.completeManagedFileDeletion(payload.id());
				return null;
			})
			.get();
	}

}
