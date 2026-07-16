package com.joshlong.mogul.api.media;

import java.io.File;

public interface Encoder<T extends EncodedFile> {

	T encode(File path);

}
