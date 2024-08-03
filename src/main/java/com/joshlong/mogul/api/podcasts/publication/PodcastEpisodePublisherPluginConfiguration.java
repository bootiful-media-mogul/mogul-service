package com.joshlong.mogul.api.podcasts.publication;

import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.podcasts.PodcastService;
import com.joshlong.mogul.api.podcasts.production.PodcastProducer;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ImportRuntimeHints(PodcastEpisodePublisherPluginConfiguration.Hints.class)
class PodcastEpisodePublisherPluginConfiguration {

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			hints.proxies()
				.registerJdkProxy(PodcastEpisodePublisherPlugin.class,
						org.springframework.beans.factory.BeanNameAware.class,
						org.springframework.aop.SpringProxy.class, org.springframework.aop.framework.Advised.class,
						org.springframework.core.DecoratingProxy.class);
		}

	}

	@Bean
	static ProducingPodcastPublisherPluginBeanPostProcessor podcastProducingBeanPostProcessor(BeanFactory beanFactory) {
		return new ProducingPodcastPublisherPluginBeanPostProcessor(beanFactory);
	}

}
