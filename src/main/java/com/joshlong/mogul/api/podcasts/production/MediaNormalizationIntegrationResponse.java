package com.joshlong.mogul.api.podcasts.production;

import com.joshlong.mogul.api.managedfiles.ManagedFile;

public record MediaNormalizationIntegrationResponse(ManagedFile input, ManagedFile output) {
}
