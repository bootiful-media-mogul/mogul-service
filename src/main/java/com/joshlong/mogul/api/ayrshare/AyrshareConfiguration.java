package com.joshlong.mogul.api.ayrshare;

import com.joshlong.mogul.api.ApiProperties;
import com.joshlong.mogul.api.Settings;
import com.joshlong.mogul.api.compositions.CompositionService;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.publications.PublicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
class AyrshareConfiguration {

	@Bean
	DefaultAyrshareService defaultAyrshareService(Settings settings, ApiProperties properties, MogulService ms,
			CompositionService cs, PublicationService ps, JdbcClient db) {
		return new DefaultAyrshareService(ms, db, settings, properties.cache().maxEntries(), cs, ps);
	}

}
