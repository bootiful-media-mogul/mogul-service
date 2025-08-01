package com.joshlong.mogul.api.media;

import com.joshlong.mogul.api.managedfiles.ManagedFile;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Map;

class DefaultMediaService implements MediaService {

	private final MessageChannel requests;

	DefaultMediaService(MessageChannel requests) {
		this.requests = requests;
	}

	@Override
	public void normalize(ManagedFile input, ManagedFile output, Map<String, Object> context) {
		var episodeMediaNormalizationRequest = MessageBuilder
			.withPayload(new MediaNormalizationRequest(input, output, context))
			.build();
		this.requests.send(episodeMediaNormalizationRequest);
	}

}
