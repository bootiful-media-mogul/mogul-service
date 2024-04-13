package com.joshlong.mogul.api.podcasts.publication;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PodcastEpisodePublisherPluginConfiguration {

	@Bean
	static ProducingPodcastPublisherPluginBeanPostProcessor podcastProducingBeanPostProcessor(BeanFactory beanFactory) {
		return new ProducingPodcastPublisherPluginBeanPostProcessor(beanFactory);
	}

}
