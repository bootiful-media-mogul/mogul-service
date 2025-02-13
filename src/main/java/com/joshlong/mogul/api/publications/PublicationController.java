package com.joshlong.mogul.api.publications;

import com.joshlong.mogul.api.*;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.utils.JsonUtils;
import com.joshlong.mogul.api.utils.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@SuppressWarnings("unchecked")
@Controller
class PublicationController<T extends Publishable> {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Settings settings;

	private final Map<String, Class<?>> publishableClasses = new ConcurrentHashMap<>();

	private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

	private final PublicationService publicationService;

	private final MogulService mogulService;

	private final Map<String, PublisherPlugin<T>> plugins = new ConcurrentHashMap<>();

	PublicationController(Settings settings, PublicationService publicationService, MogulService mogulService,
			Map<String, PublisherPlugin<?>> plugins, Map<String, PublishableRepository<?>> resolvers) {
		this.publicationService = publicationService;
		this.mogulService = mogulService;
		this.settings = settings;

		plugins.forEach((k, v) -> this.plugins.put(k, (PublisherPlugin<T>) v));

		for (var r : resolvers.entrySet()) {
			var resolver = r.getValue();
			for (var cl : ReflectionUtils.genericsFor(resolver.getClass())) {
				this.publishableClasses.put(cl.getSimpleName().toLowerCase(), cl);
			}
		}
	}

	@QueryMapping
	boolean canPublish(@Argument Long publishableId, @Argument String publishableType, @Argument String contextJson,
			@Argument String plugin) {

		var context = this.contextFromClient(contextJson);
		var publishable = (T) this.findPublishable(publishableId, publishableType);
		Assert.state(this.plugins.containsKey(plugin), "the plugin named [" + plugin + "] does not exist!");
		var resolvedPlugin = this.plugins.get(plugin);
		var configuration = this.settings.getAllValuesByCategory(mogulService.getCurrentMogul().id(), plugin);
		var combinedContext = new HashMap<>(configuration);
		for (var k : context.keySet()) {
			if (!combinedContext.containsKey(k)) {
				combinedContext.put(k, context.get(k));
			} //
			else {
				this.log.warn("refusing to add client specified context value '{}',"
						+ "because it would override a user specified setting", k);
			}
		}
		return resolvedPlugin.canPublish(combinedContext, publishable);

	}

	@MutationMapping
	boolean publish(@Argument Long publishableId, @Argument String publishableType, @Argument String contextJson,
			@Argument String plugin) {
		Assert.hasText(plugin, "the plugin named [" + plugin + "] does not exist!");
		var currentMogulId = this.mogulService.getCurrentMogul().id();
		var episode = (T) this.findPublishable(publishableId, publishableType);
		var publisherPlugin = this.plugins.get(plugin);
		Assert.state(this.plugins.containsKey(plugin), "the plugin named [" + plugin + "] does not exist!");
		var auth = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
		var runnable = (Runnable) () -> {
			SecurityContextHolder.getContext().setAuthentication(auth);
			this.mogulService.assertAuthorizedMogul(currentMogulId);
			var contextAndSettings = this.contextFromClient(contextJson);
			this.publicationService.publish(currentMogulId, episode, contextAndSettings, publisherPlugin);
		};
		this.executor.execute(runnable);
		return true;
	}

	@QueryMapping
	Collection<Publication> publicationsForPublishable(@Argument String type, @Argument Long id) {
		var publishableClass = this.publishableClassForTypeName(type);
		return this.publicationService.getPublicationsByPublicationKeyAndClass(id, publishableClass);
	}

	@SchemaMapping
	long created(Publication publication) {
		return publication.created().getTime();
	}

	@SchemaMapping
	String state(Publication publication) {
		return publication.state().name().toLowerCase();
	}

	@SchemaMapping
	Long published(Publication publication) {
		return publication.published() != null ? publication.published().getTime() : null;
	}

	@MutationMapping
	boolean unpublish(@Argument Long publicationId) {
		this.log.debug("going to unpublish the publication with id # {}", publicationId);
		var publicationById = this.publicationService.getPublicationById(publicationId);
		Assert.notNull(publicationById, "the publication should not be null");
		var resolvedPlugin = this.plugins.get(publicationById.plugin());
		Assert.notNull(resolvedPlugin, "you must specify an active plugin");
		this.publicationService.unpublish(publicationById.mogulId(), publicationById, resolvedPlugin);
		return true;

	}

	private <T extends Publishable> T findPublishable(Long id, String type) {
		if (id instanceof Number idAsNumber) {
			var aClass = publishableClassForTypeName(type);
			var mogulId = this.mogulService.getCurrentMogul().id();
			return (T) this.publicationService.resolvePublishable(mogulId, idAsNumber.longValue(), aClass);
		}
		throw new IllegalStateException("could not load Publishable with id [" + id + "] and type [" + type + "]");
	}

	private Map<String, String> contextFromClient(String json) {
		if (StringUtils.hasText(json)) {
			// @formatter:off
			return JsonUtils.read(json, new ParameterizedTypeReference<>() {});
			// @formatter:on
		}
		return new HashMap<>();
	}

	private <T extends Publishable> Class<T> publishableClassForTypeName(String type) {
		var match = (Class<T>) this.publishableClasses.getOrDefault(type.toLowerCase(), null);
		Assert.notNull(match, "couldn't find a matching class for type [" + type + "]");
		return match;
	}

}
