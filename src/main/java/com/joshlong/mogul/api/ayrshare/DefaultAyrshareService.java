package com.joshlong.mogul.api.ayrshare;

import com.joshlong.mogul.api.compositions.CompositionService;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.notifications.NotificationEvent;
import com.joshlong.mogul.api.notifications.NotificationEvents;
import com.joshlong.mogul.api.publications.PublicationCompletedEvent;
import com.joshlong.mogul.api.publications.PublicationService;
import com.joshlong.mogul.api.utils.CollectionUtils;
import com.joshlong.mogul.api.utils.JsonUtils;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * warning! do <em>not</em> make this class {@link Transactional transactional}, as a lot
 * of the implementations involve network calls and stuff that doesn't interact with a SQL
 * DB. no use hogging up a DB connection just to do HTTP IO.
 */
class DefaultAyrshareService implements AyrshareService {

	private final CompositionService compositionService;

	private final JdbcClient db;

	private final Map<Long, Ayrshare> clients;

	private final MogulService mogulService;

	private final PublicationService publicationService;

	private final Platform[] platforms;

	DefaultAyrshareService(MogulService mogulService, JdbcClient db, int maxCache,
			CompositionService compositionService, PublicationService publicationService) {
		this.mogulService = mogulService;
		this.db = db;
		this.compositionService = compositionService;
		this.clients = CollectionUtils.evictingConcurrentMap(maxCache, Duration.ofMinutes(10));
		this.publicationService = publicationService;
		this.platforms = Platform.values();
	}

	@Override
	public Platform[] platforms() {
		return this.platforms;
	}

	@Override
	public Response post(String post, Platform[] platforms, Supplier<String> keySupplier,
			Consumer<PostContext> contextConsumer) {
		var currentMogul = this.mogulService.getCurrentMogul();
		var mogulId = currentMogul.id();
		var ayrshare = this.clients.computeIfAbsent(mogulId, _ -> new Ayrshare(keySupplier.get()));
		return ayrshare.post(post, platforms, contextConsumer);
	}

	@Override
	public Platform platform(String platformCode) {
		return Platform.of(platformCode);
	}

	private boolean isAyrshare(String pluginName) {
		if (!StringUtils.hasText(pluginName))
			return true;
		var ayrsharePlugins = List.of(AyrshareConstants.PODCAST_EPISODE_AYRSHARE_PLUGIN_NAME,
				AyrshareConstants.BLOG_POST_AYRSHARE_PLUGIN_NAME, AyrshareConstants.MOGUL_STATUS_AYRSHARE_PLUGIN_NAME);
		return ayrsharePlugins.contains(pluginName);
	}

	@Override
	@Transactional
	public Collection<AyrsharePublicationComposition> getDraftAyrsharePublicationCompositionsFor(Long mogulId) {
		var accountedForPlatforms = this.getDrafts(mogulId)
			.stream() //
			.map(AyrsharePublicationComposition::platform) //
			.filter(Objects::nonNull) //
			.map(Platform::platformCode) //
			.collect(Collectors.toSet());

		for (var platform : this.platforms()) {
			var platformCode = platform.platformCode();
			if (accountedForPlatforms.contains(platformCode))
				continue;
			var gkh = new GeneratedKeyHolder();
			// this runs concurrently: publishing to N platforms tells the client N
			// times that it should go re-read the drafts. without `on conflict`, every
			// one of those requests that didn't see the others' uncommitted inserts
			// would add a second draft row, and the platform would render twice from
			// then on.
			var inserted = this.db.sql("""
					insert into ayrshare_publication_composition( mogul_id, platform, draft )
					values (?,?,true)
					on conflict (mogul_id, platform) where draft do nothing
					returning id
					""") //
				.params(mogulId, platformCode) //
				.update(gkh);
			if (inserted == 0) // somebody else created this platform's draft first
				continue;
			var newId = Objects.requireNonNull(gkh.getKey()).longValue();
			var payload = new AyrsharePublicationComposition(newId, true, null, platform, null);
			var composition = this.compositionService.compose(payload, platformCode);
			this.db.sql("update ayrshare_publication_composition set composition_id = ? where id = ?")
				.params(composition.id(), newId)
				.update();
		}

		var drafts = this.getDrafts(mogulId);
		var draftsByPlatform = drafts.stream()
			.collect(Collectors.groupingBy(AyrsharePublicationComposition::platform, Collectors.counting()));
		Assert.state(draftsByPlatform.values().stream().allMatch(count -> count == 1),
				() -> "there must be exactly one draft per platform for mogul " + mogulId + ", but got "
						+ draftsByPlatform);
		return drafts.stream() //
			.sorted(Comparator.comparing(AyrsharePublicationComposition::platform))//
			.collect(Collectors.toList());
	}

	private AyrsharePublicationCompositionResultSetExtractor buildResultSetExtractor() {
		return new AyrsharePublicationCompositionResultSetExtractor(this.compositionService::getCompositionsByIds,
				this.publicationService::getPublicationsByIds, this::platform);
	}

	private Collection<AyrsharePublicationComposition> getDrafts(Long mogulId) {
		return db.sql("select * from ayrshare_publication_composition where mogul_id = ? and draft = true")
			.param(mogulId)
			.query(this.buildResultSetExtractor());
	}

	@EventListener
	void onAyrsharePublicationCompletedEvent(PublicationCompletedEvent pce) {
		if (!this.isAyrshare(pce.publication().plugin()))
			return;

		var mogul = pce.publication().mogulId();
		var publicationContext = pce.publication().context();
		for (var platform : this.platforms()) {
			var platformCode = platform.platformCode();
			if (publicationContext.containsKey(platformCode)) {
				var compositionIdKey = platformCode + "CompositionId";
				Assert.state(publicationContext.containsKey(compositionIdKey), "the context must "
						+ "contain a valid composition id for " + platformCode + " and mogul " + mogul);
				var compositionKey = publicationContext.get(compositionIdKey);
				var compositionId = Long.parseLong(compositionKey);
				this.db.sql(
						"update ayrshare_publication_composition set draft = false, publication_id = ? where composition_id =  ? and mogul_id = ? and platform = ?")
					.params(pce.publication().id(), compositionId, mogul, platformCode)
					.update();

			}
		}

		for (var platform : this.platforms()) {
			var pc = platform.platformCode();
			if (pce.publication().context().containsKey(pc)) {
				var apce = new AyrsharePublicationCompletionEvent(pc);
				var event = NotificationEvent.systemNotificationEventFor(pce.publication().mogulId(), apce,
						Long.toString(pce.publication().id()), JsonUtils.write(Map.of("platform", pc)));
				NotificationEvents.notify(event);
			}
		}
	}

}
