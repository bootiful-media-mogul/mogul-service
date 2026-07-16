package com.joshlong.mogul.api.media;

import java.io.File;
import java.util.Map;

/**
 * represents the result of the {@code MediaService#encode(File)} operation.
 */
public interface EncodedFile {

	File file();

	Map<String, Object> context();

}
