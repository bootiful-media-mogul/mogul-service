package com.joshlong.mogul.api;

import com.joshlong.mogul.api.utils.ReflectionUtils;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.Serializable;
import java.util.HashSet;

/**
 * Repository interface for entities that can be published.
 *
 * Provides a uniform strategy for loading {@link Publishable} instances across different
 * subsystems. The logic differs based on the subsystem; e.g.: the
 * {@link com.joshlong.mogul.api.blogs.BlogService blogService} can resolve
 * {@link com.joshlong.mogul.api.blogs.Post posts}, and the
 * {@link com.joshlong.mogul.api.podcasts.PodcastService podcastService} can resolve
 * {@link com.joshlong.mogul.api.podcasts.Episode episodes}.
 *
 * Extends {@link DomainRepository} to follow the common domain pattern convention.
 *
 * @param <T> The concrete entity type that implements Publishable
 */
public interface PublishableRepository<T extends Publishable> extends DomainRepository<Publishable, T> {

}

// todo not sure if theres now not a better way to provide graalvm hints for all
// implementations of the new pattern
@Configuration
class PublishableConfiguration {

	@Bean
	static PublishableBeanFactoryInitializationAotProcessor publishableRepositoryHints() {
		return new PublishableBeanFactoryInitializationAotProcessor();
	}

	/**
	 * we need to work with instances of these Publishable classes via their .class files
	 * later on, so make sure we have metadata enough during GraalVM native runtime to
	 * make it work.
	 */
	static class PublishableBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {

		@Override
		public BeanFactoryInitializationAotContribution processAheadOfTime(
				ConfigurableListableBeanFactory beanFactory) {
			var beansOfType = beanFactory.getBeanDefinitionNames();
			var classes = new HashSet<Class<?>>();
			var generics = new HashSet<Class<?>>();
			for (var beanName : beansOfType) {
				var type = beanFactory.getType(beanName);
				if (null != type) {
					if (PublisherPlugin.class.isAssignableFrom(type)) {
						classes.add(type);
					}
					if (PublishableRepository.class.isAssignableFrom(type)) {
						classes.add(type);
					}
				}
			}
			for (var clzz : classes) {
				generics.addAll(ReflectionUtils.genericsFor(clzz));
			}
			return (generationContext, _) -> {
				var mcs = MemberCategory.values();
				var hints = generationContext.getRuntimeHints();
				for (var c : generics) {
					hints.reflection().registerType(c, mcs);
				}
				for (var c : classes) {
					hints.reflection().registerType(c, mcs);
				}
			};
		}

	}

}
