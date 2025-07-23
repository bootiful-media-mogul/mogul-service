package com.joshlong.mogul.api.utils;

import java.net.URI;
import java.net.URISyntaxException;

public abstract class UriUtils {

	public static URI uri(String uri) {
		if (uri == null || uri.isBlank())
			return null;
		try {
			return new URI(uri);
		}
		catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

}
