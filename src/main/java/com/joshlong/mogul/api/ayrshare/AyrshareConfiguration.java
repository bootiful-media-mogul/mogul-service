package com.joshlong.mogul.api.ayrshare;

import com.joshlong.mogul.api.Settings;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.utils.CollectionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

import static com.joshlong.mogul.api.ayrshare.AyrshareConstants.API_KEY_SETTING_KEY;
import static com.joshlong.mogul.api.ayrshare.AyrshareConstants.PLUGIN_NAME;

@Configuration
class AyrshareConfiguration {

	@Bean
	MogulAwareAyrshareClient multitenantMogulAwareAyrshareClient(MogulService mogulService, Settings settings) {
		return new MogulAwareAyrshareClient(mogulService, settings);
	}

}

class MogulAwareAyrshareClient implements Ayrshare {

	private final Map<Long, Ayrshare> clients = CollectionUtils.evictingConcurrentMap(100, Duration.ofMinutes(10));

	private final MogulService mogulService;

	private final Settings settings;

	MogulAwareAyrshareClient(MogulService mogulService, Settings settings) {
		this.mogulService = mogulService;
		this.settings = settings;
	}

	@Override
	public Response post(String post, Platform[] platforms, Consumer<PostContext> contextConsumer) {
		var cm = this.mogulService.getCurrentMogul();
		var ayrshare = this.clients.computeIfAbsent(cm.id(), aLong -> {
			var mogulId = cm.id();
			var settingsForTenant = settings.getAllSettingsByCategory(mogulId, PLUGIN_NAME);
			var key = settingsForTenant.get(API_KEY_SETTING_KEY).value();
			return new SimpleAyrshare(key);
		});
		return ayrshare.post(post, platforms, contextConsumer);
	}

}