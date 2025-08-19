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
 * we need a way to, given a {@link Serializable id} and a {@link Class class}, find the
 * given instance of the {@link Publishable publishable}. The logic for that will differ
 * based on the subsystem; e.g.: the {@link com.joshlong.mogul.api.blogs.BlogService
 * blogService} can resolve {@link com.joshlong.mogul.api.blogs.Post posts}, and the
 * {@link com.joshlong.mogul.api.podcasts.PodcastService podcastService} can resolve
 * {@link com.joshlong.mogul.api.podcasts.Episode episodes}. So this class helps provide a
 * uniform strategy for loading a {@link Publishable publishable}.
 *
 * @param <T>
 */
public interface PublishableRepository<T extends Publishable> {

	boolean supports(Class<?> clazz);

	T find(Long serializable);

}

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
