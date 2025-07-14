package com.joshlong.mogul.api.publications;

import com.joshlong.mogul.api.Publication;
import com.joshlong.mogul.api.Publishable;
import com.joshlong.mogul.api.PublishableRepository;
import com.joshlong.mogul.api.PublisherPlugin;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.notifications.NotificationEvent;
import com.joshlong.mogul.api.notifications.NotificationEvents;
import com.joshlong.mogul.api.utils.CollectionUtils;
import com.joshlong.mogul.api.utils.JdbcUtils;
import com.joshlong.mogul.api.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

@SuppressWarnings("unused")
@RegisterReflectionForBinding({ Publishable.class, PublisherPlugin.class, PublisherPlugin.PublishContext.class,
		PublisherPlugin.UnpublishContext.class, PublisherPlugin.Context.class })
class DefaultPublicationService implements PublicationService {

	private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Map<String, Collection<Publication>> publicationsCache = CollectionUtils.evictingConcurrentMap(10_000,
			Duration.ofHours(1));

	private final Function<SettingsLookup, Map<String, String>> settingsLookup;

	private final JdbcClient db;

	private final MogulService mogulService;

	private final RowMapper<Publication> publicationRowMapper;

	private final TextEncryptor textEncryptor;

	private final TransactionTemplate transactionTemplate;

	private final ApplicationEventPublisher publisher;

	private final Collection<PublishableRepository<?>> publishableRepositories;

	DefaultPublicationService(JdbcClient db, MogulService mogulService, TextEncryptor textEncryptor,
			TransactionTemplate tt, Function<SettingsLookup, Map<String, String>> settingsLookup,
			ApplicationEventPublisher publisher, Collection<PublishableRepository<?>> publishableRepositories) {
		this.db = db;
		this.transactionTemplate = tt;
		this.settingsLookup = settingsLookup;
		this.mogulService = mogulService;
		this.textEncryptor = textEncryptor;
		this.publicationRowMapper = new PublicationRowMapper(textEncryptor, this::outcomes);
		this.publisher = publisher;
		this.publishableRepositories = publishableRepositories;
	}

	private List<Publication.Outcome> outcomes(Long publicationId) {
		return this.db //
			.sql("select * from publication_outcome where publication_id = ? order by created ") //
			.param(publicationId)//
			.query((rs, rowNum) -> new Publication.Outcome(rs.getInt("id"), rs.getDate("created"),
					rs.getBoolean("success"), JdbcUtils.url(rs, "uri"), rs.getString("key")))
			.list();
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends Publishable> T resolvePublishable(Long mogulId, Long id, Class<T> clazz) {
		this.mogulService.assertAuthorizedMogul(mogulId);
		for (var pr : this.publishableRepositories) {
			if (pr.supports(clazz)) {
				return (T) pr.find(id);
			}
		}
		throw new IllegalStateException(
				"we couldn't resolve a Publishable with id [" + id + "] and class [" + clazz + "]");
	}

	@Override
	public <T extends Publishable> Publication unpublish(Long mogulId, Publication publication,
			PublisherPlugin<T> plugin) {
		var mogul = this.mogulService.getMogulById(mogulId);
		Assert.notNull(plugin, "the plugin must not be null");
		Assert.notNull(publication, "the publication must not be null");
		Assert.notNull(mogul, "the mogul should not be null");

		var context = publication.context();
		var newContext = new HashMap<>(context);

		try {
			var uc = new PublisherPlugin.UnpublishContext<T>(newContext, publication);
			if (plugin.unpublish(uc)) {
				var contextJson = this.textEncryptor.encrypt(JsonUtils.write(context));
				this.db.sql("update publication set state = ?, context = ? where id =? ")
					.params(Publication.State.UNPUBLISHED.name(), contextJson, publication.id())
					.update();
				this.publisher.publishEvent(new PublicationUpdatedEvent(publication.id()));

			}
		}
		catch (Exception throwable) {
			this.log.warn("couldn't unpublish {} ", publication.id(), throwable);
		}
		return this.getPublicationById(publication.id());

	}

	@Override
	public <T extends Publishable> Publication publish(Long mogulId, T payload, Map<String, String> contextAndSettings,
			PublisherPlugin<T> plugin) {
		Assert.notNull(plugin, "the plugin must not be null");
		Assert.notNull(payload, "the payload must not be null");
		var configuration = this.settingsLookup.apply(new SettingsLookup(mogulId, plugin.name()));
		var context = new ConcurrentHashMap<String, String>();
		context.putAll(configuration);
		context.putAll(contextAndSettings);

		var publicationId = (long) Objects.requireNonNull(this.transactionTemplate.execute(transactionStatus -> {
			var mogul = this.mogulService.getMogulById(mogulId);
			Assert.notNull(mogul, "the mogul should not be null");
			var contextJson = this.textEncryptor.encrypt(JsonUtils.write(context));
			var publicationData = JsonUtils.write(payload.publicationKey());
			var entityClazz = payload.getClass().getName();
			var kh = new GeneratedKeyHolder();
			this.db.sql(
					"insert into publication( state, mogul, plugin, created, published, context, payload , payload_class ) VALUES (?,?,?,?,?,?,?,?)")
				.params(Publication.State.DRAFT.name(), mogulId, plugin.name(), new Date(), null, contextJson,
						publicationData, entityClazz)
				.update(kh);
			return JdbcUtils.getIdFromKeyHolder(kh).longValue();
		}));

		this.doNotify(mogulId, publicationId, new PublicationStartedEvent(publicationId));

		var pc = PublisherPlugin.PublishContext.of(payload, context);
		plugin.publish(pc);

		return this.transactionTemplate.execute((status) -> {

			this.db.sql(" update publication set state = ? , context = ?, published = ?  where id = ?")
				.params(Publication.State.PUBLISHED.name(), textEncryptor.encrypt(JsonUtils.write(context)), new Date(),
						publicationId)
				.update();

			pc.outcomes().forEach((outcome) -> {
				this.db.sql("insert into publication_outcome(publication_id,success, uri , key ) values (?,?,?,?)")
					.params(publicationId, outcome.success(), outcome.uri().toString(), outcome.key())
					.update();
			});

			this.doNotify(mogulId, publicationId, new PublicationCompletedEvent(publicationId));

			return this.getPublicationById(publicationId);
		});

	}

	private void doNotify(Long mogulId, Long publicationId, Object event) {
		this.publisher.publishEvent(event);
		NotificationEvents.notifyAsync(
				NotificationEvent.systemNotificationEventFor(mogulId, event, Long.toString(publicationId), null));
	}

	@Override
	public Publication getPublicationById(Long publicationId) {
		for (var pc : this.publicationsCache.values())
			for (var p : pc)
				if (p.id().equals(publicationId))
					return p;
		throw new IllegalStateException(
				"we shouldn't get to this point. what are you doing looking up a publication whose ID (" + publicationId
						+ ") doesn't match? ");
	}

	private void refreshCache() {

		this.publicationsCache.clear();

		this.db.sql("select * from publication") //
			.query(this.publicationRowMapper) //
			.stream()//
			.forEach(publication -> {
				var key = this.buildPublicationCacheKey(publication.payloadClass(), publication.payload());
				this.publicationsCache.computeIfAbsent(key, cacheKey -> new ArrayList<>()).add(publication);
			});
	}

	private String buildPublicationCacheKey(Class<?> c, Serializable s) {
		return c.getName() + ":" + s.toString();
	}

	@Override
	public Collection<Publication> getPublicationsByPublicationKeyAndClass(Long publicationKey, Class<?> clazz) {
		var key = this.buildPublicationCacheKey(clazz, publicationKey);
		if (this.publicationsCache.containsKey(key)) {
			return this.publicationsCache//
				.get(key)//
				.stream()//
				.sorted(Comparator.comparing(Publication::created).reversed())//
				.toList();
		}
		return List.of();
	}

	@EventListener
	void applicationReady(ApplicationReadyEvent are) {
		this.scheduledExecutorService.submit(this::refreshCache);
	}

	@EventListener
	void updated(PublicationUpdatedEvent pue) {
		this.log.info("publicationUpdatedEvent");
		this.refreshCache();
	}

	@EventListener
	void started(PublicationStartedEvent pse) {
		this.refreshCache();
	}

	@EventListener
	void completed(PublicationCompletedEvent pce) {
		this.refreshCache();
	}

	public record SettingsLookup(Long mogulId, String category) {

	}

}
