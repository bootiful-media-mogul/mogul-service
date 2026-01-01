package com.joshlong.mogul.api.publications;

import com.joshlong.mogul.api.Publication;
import com.joshlong.mogul.api.Publishable;
import com.joshlong.mogul.api.PublisherPlugin;
import com.joshlong.mogul.api.Settings;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.utils.DateUtils;
import com.joshlong.mogul.api.utils.JsonUtils;
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

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Controller
class PublicationController<T extends Publishable> {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Settings settings;

	private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

	private final PublicationService publicationService;

	private final MogulService mogulService;

	private final Map<String, PublisherPlugin<T>> plugins = new ConcurrentHashMap<>();

	PublicationController(Settings settings, PublicationService publicationService, MogulService mogulService,
			Map<String, PublisherPlugin<?>> plugins) {
		this.publicationService = publicationService;
		this.mogulService = mogulService;
		this.settings = settings;
		plugins.forEach((k, v) -> this.plugins.put(k, (PublisherPlugin<T>) v));
	}

	@QueryMapping
	boolean canPublish(@Argument Long publishableId, @Argument String publishableType, @Argument String contextJson,
			@Argument String plugin) {
		var context = this.contextFromClient(contextJson);
		var publishable = (T) this.publicationService.resolvePublishable(mogulService.getCurrentMogul().id(),
				publishableId, publishableType);
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
		var pc = PublisherPlugin.PublishContext.of(publishable, combinedContext);
		return resolvedPlugin.canPublish(pc);
	}

	@MutationMapping
	boolean publish(@Argument Long publishableId, @Argument String publishableType, @Argument String contextJson,
			@Argument String plugin) {

		this.log.debug(
				"going to publish the publication with id # {} and type # {} and context JSON :: {} :: and plugin named {}",
				publishableId, publishableType, contextJson, plugin);

		Assert.hasText(plugin, "the plugin named [" + plugin + "] does not exist!");
		var currentMogulId = this.mogulService.getCurrentMogul().id();
		var episode = (T) publicationService.resolvePublishable(mogulService.getCurrentMogul().id(), publishableId,
				publishableType);
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
		return this.publicationService.getPublicationsByPublicationKeyAndClass(id, type);
	}

	@SchemaMapping
	String url(Publication publication) {
		if (publication.outcomes() != null && !publication.outcomes().isEmpty()) {
			var uri = publication.outcomes().getFirst().url();
			if (null == uri)
				return null;
			return uri.toString();
		}
		return null;
	}

	@SchemaMapping
	OffsetDateTime published(Publication publication) {
		return DateUtils.forDate(publication.published());
	}

	@SchemaMapping
	OffsetDateTime created(Publication publication) {
		return DateUtils.forDate(publication.created());
	}

	@SchemaMapping
	String state(Publication publication) {
		return publication.state().name().toLowerCase();
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

	private Map<String, String> contextFromClient(String json) {
		if (StringUtils.hasText(json)) {
			// @formatter:off
			var data = JsonUtils.read(json, new ParameterizedTypeReference<Map <String, Object>>() {});
            // @formatter:on
			var newMap = new HashMap<String, String>();
			for (var k : data.keySet()) {
				newMap.put(k, data.get(k).toString());
			}
			return newMap;

		}
		return new HashMap<>();
	}

}
