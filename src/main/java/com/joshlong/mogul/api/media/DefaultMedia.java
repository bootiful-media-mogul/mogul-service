package com.joshlong.mogul.api.media;

import com.joshlong.mogul.api.managedfiles.ManagedFile;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
class DefaultMedia implements Media {

	private final MessageChannel requests;

	DefaultMedia(@MediaNormalizationMessageChannel MessageChannel requests) {
		this.requests = requests;
	}

	@Override
	public void normalize(ManagedFile input, ManagedFile output, Map<String, Object> context) {
		// first we kick off the async media normalization flow
		var episodeMediaNormalizationRequest = MessageBuilder
			.withPayload(new MediaNormalizationRequest(input, output, context))
			.build();
		this.requests.send(episodeMediaNormalizationRequest);
	}

}
