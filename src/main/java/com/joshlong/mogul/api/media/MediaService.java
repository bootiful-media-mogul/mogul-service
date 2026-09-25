package com.joshlong.mogul.api.media;

import com.joshlong.mogul.api.managedfiles.ManagedFile;

import java.util.Map;

public interface MediaService {

	void normalize(ManagedFile input, ManagedFile output, Map<String, Object> context);

}
