package com.joshlong.mogul.api.ayrshare;

import com.joshlong.mogul.api.ApiProperties;
import com.joshlong.mogul.api.compositions.CompositionService;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.publications.PublicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
class AyrshareConfiguration {

	@Bean
	DefaultAyrshareService defaultAyrshareService(ApiProperties properties, MogulService ms, CompositionService cs,
			JdbcClient db, PublicationService publicationService) {
		return new DefaultAyrshareService(ms, db, properties.cache().maxEntries(), cs, publicationService);
	}

}
