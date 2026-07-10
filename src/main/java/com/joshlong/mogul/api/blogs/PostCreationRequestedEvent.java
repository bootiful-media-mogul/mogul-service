package com.joshlong.mogul.api.blogs;

import java.util.function.Consumer;

/**
 * a request to create a blog {@link Post} from external content (e.g. a podcast episode).
 * owned by the blogs module: other modules (like {@code podcasts}) publish this, and the
 * blogs module listens for it and does the actual {@link Post} creation. this keeps the
 * dependency direction one-way (publisher &rarr; blogs) and avoids a cycle.
 * <p>
 * this is an in-process <em>request</em> event: the blog listener runs synchronously and
 * hands the newly created {@link Post} back to the publisher through
 * {@link #onCreated()}, so the caller (e.g. a publisher plugin) can record the resulting
 * post's path as a publication outcome. because it carries a callback it is delivered
 * synchronously via a plain {@code @EventListener} (never persisted/externalized).
 */
public record PostCreationRequestedEvent(Long mogulId, Long blogId, String title, String content, String summary,
		Consumer<Post> onCreated) {
}
