package com.joshlong.mogul.api.utils.jdbc;

import com.joshlong.mogul.api.utils.RewindableResultSet;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.SpringProxy;
import org.springframework.aop.framework.Advised;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.DecoratingProxy;

@Configuration
@ImportRuntimeHints(RewindableResultSetsAutoConfiguration.Hints.class)
class RewindableResultSetsAutoConfiguration {

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(@NonNull RuntimeHints hints, @Nullable ClassLoader classLoader) {

			var classes = new Class<?>[] { RewindableResultSet.class, SpringProxy.class, Advised.class,
					DecoratingProxy.class };
			hints.proxies().registerJdkProxy(classes);

			var mc = MemberCategory.values();
			for (var c : classes)
				hints.reflection().registerType(c, mc);

		}

	}

}
