package com.joshlong.mogul.api.ayrshare;

import com.joshlong.mogul.api.Settings;
import com.joshlong.mogul.api.compositions.CompositionService;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.publications.PublicationService;
import com.joshlong.mogul.api.utils.CollectionUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

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
		this.settings = settings;
		this.clients = CollectionUtils.evictingConcurrentMap(maxCache, Duration.ofMinutes(10));
		this.ayrsharePublicationCompositionRowMapper = this.ayrsharePublicationCompositionRowMapper(compositionService,
				publicationService);
		this.platforms = Platform.values();
	}

	private AyrsharePublicationCompositionRowMapper ayrsharePublicationCompositionRowMapper(
			CompositionService compositionService, PublicationService publicationService) {
		return new AyrsharePublicationCompositionRowMapper(compositionService::getCompositionById,
				publicationService::getPublicationById, this::platform);
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

		// todo do upsert, get id, find the entity, check to see if there's a composition
		// or not.
		// if not, create one
		var sql = """
				insert into ayrshare_publication_composition( mogul_id, platform ,draft ) values (?,?,true)
				on conflict on constraint ayrshare_publication_composition_mogul_id_platform_draft_key
				do update set draft = true
				returning id
				""";
		var kh = new GeneratedKeyHolder();
		this.db.sql(sql).params(mogulId, platform.platformCode().toLowerCase()).update(kh);
		return Objects.requireNonNull(kh.getKey()).longValue();
	}

	private List<AyrsharePublicationComposition> getAyrsharePublicationCompositionsFor(List<Long> aspcIds) {
		var idsPlaceholders = aspcIds.stream().map(_ -> "?").collect(Collectors.joining(","));
		var idsParams = aspcIds.toArray(_ -> new Long[0]);
		return this.db //
			.sql("select * from ayrshare_publication_composition where id in (" + idsPlaceholders + ")") //
			.params((Long[]) idsParams)
			.query(this.ayrsharePublicationCompositionRowMapper)
			.list();
	}

	@Transactional
	@Override
	public Collection<AyrsharePublicationComposition> getDraftAyrsharePublicationCompositionsFor(Long mogulId) {
		var ids = new ArrayList<Long>();
		// let's do some upserts for the compositions based on Platform
		for (var platform : this.platforms()) {
			ids.add(this.doUpsert(mogulId, platform));
		}
		return getAyrsharePublicationCompositionsFor(ids);
	}

}
