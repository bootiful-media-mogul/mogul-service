package com.joshlong.mogul.api;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public interface PublisherPlugin<T extends Publishable> {

	/**
	 * well known headers for the encrypted context data.
	 */
	String CONTEXT_URL = "url";

	String name();

	Set<String> getRequiredSettingKeys();

	default boolean isConfigurationValid(Map<String, String> context) {
		var required = this.getRequiredSettingKeys();

		if (context == null)
			context = new HashMap<>();

		if (required == null)
			required = new HashSet<>();

		var good = true;
		for (var k : required) {
			if (!context.containsKey(k)) {
				good = false;
				break;
			}
		}
		return good;
	}

	default boolean canPublish(Map<String, String> context, T payload) {
		return isConfigurationValid(context) && payload != null;
	}

	/**
	 * should we preserve record of this publication? If the publication produces
	 * something we can't <em>revisit</em> or <em>review</em>, then there's no point in
	 * recording it.
	 */
	default boolean shouldRecordPublication() {
		return true;
	}

	void publish(Map<String, String> context, T payload);

	boolean unpublish(Map<String, String> context, Publication publication);

}
