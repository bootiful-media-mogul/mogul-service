package com.joshlong.mogul.api;

import org.jspecify.annotations.NonNull;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public interface PublisherPlugin<T extends Publishable> {

	String name();

	default Set<PublisherSetting> pluginSettings() {
		return Set.of();
	}

	default boolean isConfigurationValid(Map<String, String> context) {
		var required = this.pluginSettings()
			.stream()
			.filter(PublisherSetting::required)
			.map(PublisherSetting::name)
			.toList();

		if (context == null)
			context = new HashMap<>();

		var good = true;
		for (var k : required) {
			if (!context.containsKey(k)) {
				good = false;
				break;
			}
		}
		return good;
	}

	default boolean canPublish(PublishContext<T> publishContext) {
		return isConfigurationValid(publishContext.context()) && publishContext.payload() != null;
	}

	void publish(PublishContext<T> publishContext);

	boolean unpublish(UnpublishContext<T> uc);

	record PublisherSetting(boolean required, String name) {
	}

	class Context<T> {

		private final Map<String, String> context;

		Context(Map<String, String> inputContext) {
			this.context = null == inputContext ? new ConcurrentHashMap<>() : inputContext;
		}

		public Map<String, String> context() {
			return this.context;
		}

	}

	class UnpublishContext<T> extends Context<T> {

		private final Publication publication;

		public UnpublishContext(Map<String, String> inputContext, Publication publication) {
			super(inputContext);
			this.publication = publication;
		}

		public Publication publication() {
			return this.publication;
		}

	}

	class PublishContext<T> extends Context<T> {

		private final T payload;

		private final List<Outcome> outcomes = new ArrayList<>();

		PublishContext(T payload, Map<String, String> inputContext) {
			super(inputContext);
			this.payload = payload;
		}

		public static <T> PublishContext<T> of(T payload, Map<String, String> c) {
			return new PublishContext<>(payload, c);
		}

		public T payload() {
			return this.payload;
		}

		public PublishContext<T> success(String outcomeKey, URI outcome) {
			return this.outcome(outcomeKey, true, outcome, null);
		}

		public PublishContext<T> failure(String outcomeKey, String errorMessageFromServer) {
			return this.outcome(outcomeKey, false, null, errorMessageFromServer);
		}

		private PublishContext<T> outcome(String outcomeKey, boolean success, URI outcome, String errorMessage) {
			this.outcomes.add(new Outcome(outcome, outcomeKey, success, errorMessage));
			return this;
		}

		@NonNull
		public List<Outcome> outcomes() {
			return this.outcomes;
		}

		public record Outcome(URI uri, String key, boolean success, String serverErrorMessage) {
		}

	}

}
