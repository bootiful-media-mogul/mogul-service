package com.joshlong.mogul.api.podcasts.production;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @ConcurrencyLimit} is advice, and advice that fails to apply fails silently --
 * the render would simply run unthrottled and nothing would say so until a burst of
 * publications filled the pod's ephemeral storage. so assert the proxy is really there.
 * <p>
 * the annotation is only honoured on calls that arrive through the proxy;
 * {@code ProducingPodcastPublisherPluginBeanPostProcessor} reaches the producer with
 * {@code beanFactory.getBean(PodcastProducer.class)}, so it does.
 */
@SpringBootTest
class PodcastProducerConcurrencyLimitTest {

	@Autowired
	private PodcastProducer podcastProducer;

	@Autowired
	private Environment environment;

	@Test
	void theRenderIsProxiedSoTheLimitCanApply() {
		assertThat(AopUtils.isAopProxy(this.podcastProducer))
			.as("PodcastProducer must be advised, or @ConcurrencyLimit on produce() is inert")
			.isTrue();
	}

	@Test
	void theRenderCeilingIsConfiguredAndConservative() {
		var limit = this.environment.getProperty("mogul.podcasts.production.concurrency", Integer.class);
		assertThat(limit).isNotNull().isPositive();
		// production expands every segment to uncompressed wav on local disk, so this
		// ceiling guards the 10Gi ephemeral-storage limit. it should stay well below the
		// lighter normalization ceiling.
		assertThat(limit).isLessThanOrEqualTo(2);
	}

}
