package com.joshlong.mogul.api.ai;

import java.util.List;

record ImageGenerationResponse(long created, List<Image> data) {
}
