package com.joshlong.mogul.api.transcription.audio;

import org.springframework.core.io.Resource;

record TranscriptionSegment(Resource audio, int order, long startInMilliseconds, long stopInMilliseconds) {
}
