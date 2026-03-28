package com.joshlong.mogul.api.ayrshare;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class PostContext {

	final AtomicReference<Instant> scheduledDate = new AtomicReference<>();

	final AtomicReference<String> idempotencyKey = new AtomicReference<>();

	final List<URI> mediaUris = new ArrayList<>();

	final Map<String, String> customHeaders = new ConcurrentHashMap<>();

	public PostContext customHeader(String name, String value) {
		this.customHeaders.put(name, value);
		return this;
	}

	public PostContext customHeaders(Map<String, String> customHeaders) {
		this.customHeaders.putAll(customHeaders);
		return this;
	}

	public PostContext scheduledDate(Instant scheduledDate) {
		this.scheduledDate.set(scheduledDate);
		return this;
	}

	public PostContext idempotencyKey(String idempotencyKey) {
		this.idempotencyKey.set(idempotencyKey);
		return this;
	}

	public PostContext media(URI... mediaUris) {
		if (mediaUris != null && mediaUris.length > 0)
			this.mediaUris.addAll(Arrays.asList(mediaUris));
		return this;
	}

}
