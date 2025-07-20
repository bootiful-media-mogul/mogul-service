package com.joshlong.mogul.api.ayrshare;

import com.joshlong.mogul.api.Settings;
import com.joshlong.mogul.api.compositions.CompositionService;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.notifications.NotificationEvent;
import com.joshlong.mogul.api.notifications.NotificationEvents;
import com.joshlong.mogul.api.publications.PublicationCompletedEvent;
import com.joshlong.mogul.api.publications.PublicationService;
import com.joshlong.mogul.api.publications.PublicationStartedEvent;
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

	private final CompositionService compositionService;

	private final JdbcClient db;

	private final Map<Long, Ayrshare> clients;

	private final Settings settings;

	private final MogulService mogulService;

	private final AyrsharePublicationCompositionRowMapper ayrsharePublicationCompositionRowMapper;

	private final Platform[] platforms;

	DefaultAyrshareService(MogulService mogulService, JdbcClient db, Settings settings, int maxCache,
			CompositionService compositionService, PublicationService publicationService) {
		this.mogulService = mogulService;
		this.db = db;
		this.compositionService = compositionService;
		this.settings = settings;
		this.clients = CollectionUtils.evictingConcurrentMap(maxCache, Duration.ofMinutes(10));
		this.ayrsharePublicationCompositionRowMapper = new AyrsharePublicationCompositionRowMapper(
				compositionService::getCompositionById, publicationService::getPublicationById, this::platform);
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

	private Long doUpsert(Long mogulId, Platform platform) {
		Assert.notNull(platform, "platform is null");
		Assert.notNull(mogulId, "mogulId is null");

		// new code
		var apc = this.db
			.sql("select * from ayrshare_publication_composition where mogul_id = ? and platform = ? and draft = true")
			.params(mogulId, platform.platformCode())
			.query(this.ayrsharePublicationCompositionRowMapper)
			.list();
		if (apc.size() == 1) { // either one's already been created...
			return apc.getFirst().id();
		}
		// or we need to create one...
		else {
			Assert.isTrue(apc.isEmpty(),
					"there should be no more than one draft APC for " + mogulId + " and " + platform.platformCode());
			var gkh = new GeneratedKeyHolder();
			this.db.sql(
					"insert into ayrshare_publication_composition( mogul_id, platform ,draft ) values (?,?,true) returning id")
				.params(mogulId, platform.platformCode())
				.update(gkh);
			return Objects.requireNonNull(gkh.getKey()).longValue();
		}

	}

	private List<AyrsharePublicationComposition> getAyrsharePublicationCompositionsFor(List<Long> aspcIds) {
		var idsPlaceholders = aspcIds.stream().map(_ -> "?").collect(Collectors.joining(","));
		var idsParams = aspcIds.toArray(_ -> new Long[0]);
		return this.db //
			.sql("select * from ayrshare_publication_composition where id in (" + idsPlaceholders
					+ ") order by platform") //
			.params((Long[]) idsParams)
			.query(this.ayrsharePublicationCompositionRowMapper)
			.list();
	}

	private boolean isAyrshare(String pluginName) {
		return StringUtils.hasText(pluginName) && pluginName.equalsIgnoreCase(AyrshareConstants.PLUGIN_NAME);
	}

	@Override
	@Transactional
	public Collection<AyrsharePublicationComposition> getDraftAyrsharePublicationCompositionsFor(Long mogulId) {
		var ids = new ArrayList<Long>();
		// let's do some upserts for the compositions based on Platform
		for (var platform : this.platforms()) {
			ids.add(this.doUpsert(mogulId, platform));
		}
		for (var ayrsharePublicationComposition : getAyrsharePublicationCompositionsFor(ids)) {
			if (ayrsharePublicationComposition.composition() == null) {
				var composition = this.compositionService.compose(ayrsharePublicationComposition,
						ayrsharePublicationComposition.platform().platformCode());
				Assert.notNull(composition, "the composition must not be null");
				this.db.sql("update ayrshare_publication_composition set composition_id = ? where id = ?")
					.params(composition.id(), ayrsharePublicationComposition.id())
					.update();
			}
		}
		return getAyrsharePublicationCompositionsFor(ids);
	}

	@EventListener
	void onAyrsharePublicationCompletedEvent(PublicationCompletedEvent pce) throws Exception {
		if (!isAyrshare(pce.publication().plugin()))
			return;
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

	@EventListener
	void onAyrsharePublicationStartedEvent(PublicationStartedEvent pse) throws Exception {
		if (!isAyrshare(pse.publication().plugin()))
			return;
		var mogul = pse.publication().mogulId();
		var ctx = pse.publication().context();
		for (var platform : this.platforms()) {
			var platformCode = platform.platformCode();
			if (ctx.containsKey(platformCode)) {
				var compositionIdKey = platformCode + "CompositionId";
				Assert.state(ctx.containsKey(compositionIdKey), "the context must "
						+ "contain a valid composition id for " + platformCode + " and mogul " + mogul);
				var compositionId = Long.parseLong(ctx.get(compositionIdKey));
				this.db.sql(
						"update ayrshare_publication_composition set draft = false, publication_id = ? where composition_id =  ? and mogul_id = ? and platform = ?")
					.params(pse.publication().id(), compositionId, mogul, platformCode)
					.update();
			}
			// todo should we send a NotificationEvent telling the Ayrshare plugin to
			// reload so it gets new draft APCs?
			// or should the Ayrshare plugin simply listen for the
			// publication-completed-event ?
		}

	}

}

record AyrsharePublicationCompletionEvent(String platform) {
}