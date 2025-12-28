package com.joshlong.mogul.api;

import com.joshlong.mogul.api.utils.ReflectionUtils;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;

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
