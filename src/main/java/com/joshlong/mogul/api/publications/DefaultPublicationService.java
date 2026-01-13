package com.joshlong.mogul.api.publications;

import com.joshlong.mogul.api.*;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.notifications.NotificationEvent;
import com.joshlong.mogul.api.notifications.NotificationEvents;
import com.joshlong.mogul.api.utils.CollectionUtils;
import com.joshlong.mogul.api.utils.JdbcUtils;
import com.joshlong.mogul.api.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
@RegisterReflectionForBinding({ Publishable.class, PublisherPlugin.class, PublisherPlugin.PublishContext.class,
		PublisherPlugin.UnpublishContext.class, PublisherPlugin.Context.class })
class DefaultPublicationService extends AbstractDomainService<Publishable, PublishableResolver<?>>
		implements PublicationService {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Function<SettingsLookup, Map<String, String>> settingsLookup;

	private final JdbcClient db;

	private final MogulService mogulService;

	private final TextEncryptor textEncryptor;

	private final TransactionTemplate transactionTemplate;

	private final ApplicationEventPublisher publisher;

	DefaultPublicationService(JdbcClient db, MogulService mogulService, TextEncryptor textEncryptor,
			TransactionTemplate tt, Function<SettingsLookup, Map<String, String>> settingsLookup,
			Map<String, PublishableResolver<?>> resolvers, ApplicationEventPublisher publisher

	) {
		super(resolvers.values());
		this.db = db;
		this.transactionTemplate = tt;
		this.settingsLookup = settingsLookup;
		this.mogulService = mogulService;
		this.textEncryptor = textEncryptor;
		this.publisher = publisher;
	}

	/*
	 * do <EM>NOT</EM> make this a shared class variable! there's <EM>state</EM> in the
	 * {@link PublicationRowMapper} and you'll see duplicate records if this is used
	 * across more than one request
	 */
	private PublicationRowMapper getPublicationRowMapper() {
		return new PublicationRowMapper(this.db, this.textEncryptor);
	}

	@Override
	public <T extends Publishable> T resolvePublishable(Long mogulId, Long id, String clazz) {
		return (T) this.resolvePublishable(mogulId, id, this.classForType(clazz));
	}

	@Override
	public <T extends Publishable> T resolvePublishable(Long mogulId, Long id, Class<T> clazz) {
		this.mogulService.assertAuthorizedMogul(mogulId);
		return findEntity(clazz, id);
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
				this.publisher.publishEvent(new PublicationUpdatedEvent(publication));
			}
		} //
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
			var publicationData = JsonUtils.write(payload.publishableId());
			var entityClazz = payload.getClass().getName();
			var kh = new GeneratedKeyHolder();
			this.db.sql(
					"insert into publication( state, mogul_id, plugin, created, published, context, payload , payload_class ) VALUES (?,?,?,?,?,?,?,?)")
				.params(Publication.State.DRAFT.name(), mogulId, plugin.name(), new Date(), null, contextJson,
						publicationData, entityClazz)
				.update(kh);
			return JdbcUtils.getIdFromKeyHolder(kh).longValue();
		}));

		this.doNotify(mogulId, publicationId, new PublicationStartedEvent(this.getPublicationById(publicationId)));

		var pc = PublisherPlugin.PublishContext.of(payload, context);
		try {
			plugin.publish(pc);
		} //
		catch (Throwable throwable) {
			pc.failure(plugin.name(), throwable.getMessage());
			this.log.warn("couldn't publish {} ", publicationId, throwable);
		}

		return this.transactionTemplate.execute((status) -> {

			this.db.sql(" update publication set state = ? , context = ?, published = ?  where id = ?")
				.params(Publication.State.PUBLISHED.name(), textEncryptor.encrypt(JsonUtils.write(context)), new Date(),
						publicationId)
				.update();

			pc.outcomes().forEach((outcome) -> {
				this.db.sql(
						"insert into publication_outcome(publication_id, success, uri , key ,server_error_message ) values (?,?,?,?,?)")
					.params(publicationId, outcome.success(), outcome.uri() != null ? outcome.uri().toString() : null,
							outcome.key(), outcome.serverErrorMessage())
					.update();
			});

			this.doNotify(mogulId, publicationId,
					new PublicationCompletedEvent(this.getPublicationById(publicationId)));

			return this.getPublicationById(publicationId);
		});

	}

	private void doNotify(Long mogulId, Long publicationId, Object event) {
		this.publisher.publishEvent(event);
		NotificationEvents.notifyAsync(
				NotificationEvent.systemNotificationEventFor(mogulId, event, Long.toString(publicationId), "{}"));
	}

	@Override
	public Publication getPublicationById(Long publicationId) {
		var all = this.db //
			.sql("select * from publication where id = ?") //
			.param(publicationId) //
			.query(this.getPublicationRowMapper()) //
			.list();
		if (all.isEmpty()) {
			return null;
		}
		return all.getFirst();

	}

	@Override
	public Map<Long, Publication> getPublicationsByIds(Collection<Long> badIds) {
		var ids = badIds.stream().filter(id -> id > 0).collect(Collectors.toSet());
		if (ids.isEmpty() || ids.stream().noneMatch(id -> id > 0))
			return Collections.emptyMap();
		var map = new HashMap<Long, Publication>();
		var collectedIds = CollectionUtils.join(ids, ",");
		var pubs = this.db //
			.sql("select * from publication p where p.id in (" + collectedIds + ") ")
			// todo fix this there's a way to do an Array
			//
			.query(this.getPublicationRowMapper()) //
			.list();
		for (var p : pubs)
			map.put(p.id(), p);

		return map;
	}

	@Override
	public Collection<Publication> getPublicationsByPublicationKeyAndClass(Long publicationKey, Class<?> clazz) {
		var publications = this.db //
			.sql("select * from publication where payload = ? and payload_class = ? order by created desc") //
			.params(Long.toString(publicationKey), clazz.getName())//
			.query(this.getPublicationRowMapper()) //
			.list();
		for (var p : publications)
			this.log.debug("found publication {} with created {} and published {}", p, p.created(), p.published());
		return publications;
	}

	@Override
	public Collection<Publication> getPublicationsByPublicationKeyAndClass(Long publicationKey, String clazz) {
		return this.getPublicationsByPublicationKeyAndClass(publicationKey, this.classForType(clazz));
	}

	public record SettingsLookup(Long mogulId, String category) {

	}

}
