package com.joshlong.mogul.api.ayrshare;

import com.joshlong.mogul.api.Settings;
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
import java.util.stream.Collectors;

import static com.joshlong.mogul.api.ayrshare.AyrshareConstants.API_KEY_SETTING_KEY;
import static com.joshlong.mogul.api.ayrshare.AyrshareConstants.PLUGIN_NAME;

/**
 * warning! do <em>not</em> make this class {@link Transactional transactional}, as a lot
 * of the implementations involve network calls and stuff that doesn't interact with a SQL
 * DB. no use hogging up a connection just to do HTTP IO.
 */

class DefaultAyrshareService implements AyrshareService {

	private final PublicationService publicationService;

	private final CompositionService compositionService;

	private final JdbcClient db;

	private final Map<Long, Ayrshare> clients;

	private final Settings settings;

	private final MogulService mogulService;

	private final Platform[] platforms;

	DefaultAyrshareService(MogulService mogulService, JdbcClient db, Settings settings, int maxCache,
			CompositionService compositionService, PublicationService publicationService) {
		this.mogulService = mogulService;
		this.db = db;
		this.compositionService = compositionService;
		this.settings = settings;
		this.clients = CollectionUtils.evictingConcurrentMap(maxCache, Duration.ofMinutes(10));
		this.publicationService = publicationService;
		this.platforms = Platform.values();
	}

	@Override
	public Platform[] platforms() {
		return this.platforms;
	}

	@Override
	public Response post(String post, Platform[] platforms, Consumer<PostContext> contextConsumer) {
		var currentMogul = this.mogulService.getCurrentMogul();
		var mogulId = currentMogul.id();
		var ayrshare = this.clients.computeIfAbsent(mogulId, _ -> {
			var settingsForTenant = settings.getAllSettingsByCategory(mogulId, PLUGIN_NAME);
			var key = settingsForTenant.get(API_KEY_SETTING_KEY).value();
			return new Ayrshare(key);
		});
		return ayrshare.post(post, platforms, contextConsumer);
	}

	@Override
	public Platform platform(String platformCode) {
		return Platform.of(platformCode);
	}

	private boolean isNotAyrshare(String pluginName) {
		return !StringUtils.hasText(pluginName) || !pluginName.equalsIgnoreCase(AyrshareConstants.PLUGIN_NAME);
	}

	@Override
	@Transactional
	public Collection<AyrsharePublicationComposition> getDraftAyrsharePublicationCompositionsFor(Long mogulId) {
		var accountedForPlatforms = new HashMap<String, Boolean>();
		var list = this.getDrafts(mogulId);
		for (var p : platforms()) {
			accountedForPlatforms.put(p.platformCode(), false);
		}
		for (var ayrsharePublicationComposition : list) {
			accountedForPlatforms.put(ayrsharePublicationComposition.platform().platformCode(), true);
		}
		var newIds = new ArrayList<Long>();
		for (var platform : accountedForPlatforms.keySet()) {
			if (!accountedForPlatforms.get(platform)) {
				var gkh = new GeneratedKeyHolder();
				this.db.sql(
						"insert into ayrshare_publication_composition( mogul_id, platform ,draft ) values (?,?,true) returning id")
					.params(mogulId, platform)
					.update(gkh);
				var newId = Objects.requireNonNull(gkh.getKey()).longValue();
				var payload = new AyrsharePublicationComposition(newId, true, null, Platform.of(platform), null);
				var composition = this.compositionService.compose(payload, platform);
				this.db.sql("update ayrshare_publication_composition set composition_id = ? where id = ?")
					.params(composition.id(), newId)
					.update();
				newIds.add(newId);
			}
		}

		var allIds = (List<Long>) new ArrayList<Long>();
		allIds.addAll(newIds);
		for (var oid : list)
			allIds.add(oid.id());
		var drafts = this.getDrafts(mogulId);
		Assert.state(allIds.size() == drafts.size(), "allIds.size() == drafts.size()");
		Assert.state(
				drafts.stream().map(AyrsharePublicationComposition::id).collect(Collectors.toSet()).containsAll(allIds),
				"allIds == drafts");
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
		if (isNotAyrshare(pce.publication().plugin()))
			return;

		var mogul = pce.publication().mogulId();
		var ctx = pce.publication().context();
		for (var platform : this.platforms()) {
			var platformCode = platform.platformCode();
			if (ctx.containsKey(platformCode)) {
				var compositionIdKey = platformCode + "CompositionId";
				Assert.state(ctx.containsKey(compositionIdKey), "the context must "
						+ "contain a valid composition id for " + platformCode + " and mogul " + mogul);
				var compositionId = Long.parseLong(ctx.get(compositionIdKey));
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
