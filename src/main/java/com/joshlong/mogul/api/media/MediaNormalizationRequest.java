package com.joshlong.mogul.api.media;

import com.joshlong.mogul.api.managedfiles.ManagedFile;

import java.util.Map;

record MediaNormalizationRequest(ManagedFile in, ManagedFile out, Map<String, Object> context) {
}
