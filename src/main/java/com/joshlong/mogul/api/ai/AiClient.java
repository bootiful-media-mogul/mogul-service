package com.joshlong.mogul.api.ai;

import org.springframework.core.io.Resource;

public interface AiClient {

	String transcribe(Resource audio);

	String chat(String prompt);

	Resource render(String prompt, ImageSize imageSize);

}
