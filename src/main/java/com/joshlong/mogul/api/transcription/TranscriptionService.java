package com.joshlong.mogul.api.transcription;

import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.Transcription;

public interface TranscriptionService {

	<T extends Transcribable> Transcription transcribe(T payload);

}
