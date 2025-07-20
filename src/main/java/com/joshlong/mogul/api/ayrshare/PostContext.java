package com.joshlong.mogul.api.ayrshare;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class PostContext {

	final AtomicReference<Instant> scheduledDate = new AtomicReference<>();

	final AtomicReference<String> idempotencyKey = new AtomicReference<>();

	final List<URI> mediaUris = new ArrayList<>();

	public PostContext scheduledDate(Instant scheduledDate) {
		this.scheduledDate.set(scheduledDate);
		return this;
	}

	public PostContext idempotencyKey(String idempotencyKey) {
		this.idempotencyKey.set(idempotencyKey);
		return this;
	}

	public PostContext media(URI... mediaUris) {
		if (mediaUris != null)
			this.mediaUris.addAll(Arrays.asList(mediaUris));
		return this;
	}

}
