package com.joshlong.mogul.api;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * the type map is what turns a name from the wire -- {@code "segment"}, {@code "episode"}
 * -- back into a class, and it is built once, in a constructor, from whatever the
 * resolvers happen to look like. a resolver that is {@code @Transactional} arrives as a
 * CGLIB subclass whose own superclass carries no type arguments, so the generics walk
 * found nothing and the resolver silently contributed no entry at all. the failure
 * surfaced later and somewhere else, as a transcription reply that could not name its own
 * payload class.
 */
class AbstractDomainServiceTypeMapTest {

	private record Thing(Long id) implements Transcribable {
		@Override
		public Long transcribableId() {
			return this.id();
		}
	}

	private static class ThingResolver extends AbstractTranscribableResolver<Thing> {

		ThingResolver() {
			super(Thing.class);
		}

		@Override
		public Thing find(Long key) {
			return new Thing(key);
		}

		@Override
		public Audio audio(Long key) {
			return new Audio("a-bucket", "a-key", true);
		}

	}

	/** exists only to make the protected lookup reachable from a test. */
	private static class Service extends AbstractDomainService<Transcribable, TranscribableResolver<?>> {

		Service(TranscribableResolver<?> resolver) {
			super(List.of(resolver));
		}

		Class<?> lookup(String type) {
			return this.classForType(type);
		}

	}

	private static TranscribableResolver<?> proxied(TranscribableResolver<?> target) {
		var factory = new ProxyFactory(target);
		// what @Transactional leaves behind: a subclass, not an interface proxy.
		factory.setProxyTargetClass(true);
		return (TranscribableResolver<?>) factory.getProxy();
	}

	@Test
	void aPlainResolverIsRegisteredUnderItsTypeName() {
		assertThat(new Service(new ThingResolver()).lookup("thing")).isEqualTo(Thing.class);
	}

	@Test
	void aProxiedResolverIsRegisteredTheSameWay() {
		assertThat(new Service(proxied(new ThingResolver())).lookup("thing")).isEqualTo(Thing.class);
	}

}
