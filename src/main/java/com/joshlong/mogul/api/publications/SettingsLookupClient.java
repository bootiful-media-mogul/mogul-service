package com.joshlong.mogul.api.publications;

import com.joshlong.mogul.api.Settings;

import java.util.Map;
import java.util.function.Function;

class SettingsLookupClient implements Function<DefaultPublicationService.SettingsLookup, Map<String, String>> {

	private final Settings settings;

	public SettingsLookupClient(Settings settings) {
		this.settings = settings;
	}

	@Override
	public Map<String, String> apply(DefaultPublicationService.SettingsLookup settingsLookup) {
		return this.settings.getAllValuesByCategory(settingsLookup.mogulId(), settingsLookup.category());
	}

}
