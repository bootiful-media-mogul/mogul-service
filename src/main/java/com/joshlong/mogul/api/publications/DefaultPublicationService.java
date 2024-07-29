package com.joshlong.mogul.api.publications;

import com.joshlong.mogul.api.Publication;
import com.joshlong.mogul.api.Publishable;
import com.joshlong.mogul.api.PublisherPlugin;
import com.joshlong.mogul.api.Settings;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.utils.JdbcUtils;
import com.joshlong.mogul.api.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static com.joshlong.mogul.api.PublisherPlugin.CONTEXT_URL;

@Configuration
class DefaultPublicationServiceConfiguration {

	@Bean
	DefaultPublicationService defaultPublicationService(JdbcClient client, MogulService mogulService,
			TextEncryptor textEncryptor, Settings settings, Map<String, PublisherPlugin<?>> plugins) {
		var lookup = new SettingsLookupClient(settings);
		return new DefaultPublicationService(client, mogulService, textEncryptor, lookup, plugins);
	}

}

@Transactional
@RegisterReflectionForBinding({ Publishable.class, PublisherPlugin.class })
class DefaultPublicationService implements PublicationService {

	record SettingsLookup(Long mogulId, String category) {
	}

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Map<String, PublisherPlugin<?>> plugins = new ConcurrentHashMap<>();

	private final Function<SettingsLookup, Map<String, String>> settingsLookup;

	private final JdbcClient db;

	private final MogulService mogulService;

	private final RowMapper<Publication> publicationRowMapper;

	private final TextEncryptor textEncryptor;

	DefaultPublicationService(JdbcClient db, MogulService mogulService, TextEncryptor textEncryptor,
			Function<SettingsLookup, Map<String, String>> settingsLookup, Map<String, PublisherPlugin<?>> plugins) {
		this.db = db;
		this.settingsLookup = settingsLookup;
		this.mogulService = mogulService;
		this.textEncryptor = textEncryptor;
		this.plugins.putAll(plugins);
		this.publicationRowMapper = new PublicationRowMapper(textEncryptor);
		Assert.notNull(this.db, "the JdbcClient must not be null");
		Assert.notNull(this.mogulService, "the mogulService must not be null");
		Assert.notNull(this.textEncryptor, "the textEncryptor must not be null");
		Assert.notNull(this.settingsLookup, "the settings must not be null");
		if (this.plugins.isEmpty())
			this.log.warn("there are no plugins for publication!");
	}

	@Override
	public <T extends Publishable> Publication publish(Long mogulId, T payload, Map<String, String> contextAndSettings,
			PublisherPlugin<T> plugin) {
		var mogul = this.mogulService.getMogulById(mogulId);
		Assert.notNull(plugin, "the plugin must not be null");
		Assert.notNull(payload, "the payload must not be null");
		Assert.notNull(mogul, "the mogul should not be null");

		var configuration = this.settingsLookup
			.apply(new SettingsLookup(this.mogulService.getCurrentMogul().id(), plugin.name()));
		var context = new ConcurrentHashMap<String, String>();
		context.putAll(configuration);
		context.putAll(contextAndSettings);

		var contextJson = this.textEncryptor.encrypt(JsonUtils.write(context));
		var publicationData = JsonUtils.write(payload.publicationKey());
		var entityClazz = payload.getClass().getName();
		var kh = new GeneratedKeyHolder();
		this.db.sql(
				"insert into publication(mogul_id, plugin, created, published, context, payload , payload_class) VALUES (?,?,?,?,?,?,?)")
			.params(mogulId, plugin.name(), new Date(), null, contextJson, publicationData, entityClazz)
			.update(kh);

		var publicationId = JdbcUtils.getIdFromKeyHolder(kh).longValue();

		plugin.publish(context, payload);

		this.log.debug("finished publishing with plugin {}.", plugin.name());
		contextJson = this.textEncryptor.encrypt(JsonUtils.write(context));
		this.db.sql(" update publication set context =?, published = ?  where id = ?")
			.params(contextJson, new Date(), publicationId)
			.update(kh);

		var url = context.getOrDefault(CONTEXT_URL, null);
		if (null != url) {
			this.db.sql(" update publication set url =?   where id = ?").params(url, publicationId).update(kh);
		}
		var publication = this.getPublicationById(publicationId);
		this.log.debug("writing publication out: {}", publication);
		return publication;
	}

	@Override
	public Publication getPublicationById(Long publicationId) {
		return this.db //
			.sql("select * from publication where id = ? ") //
			.params(publicationId)//
			.query(this.publicationRowMapper)//
			.single();
	}

	@Override
	public Collection<Publication> getPublicationsByPublicationKeyAndClass(Serializable publicationKey,
			Class<?> clazz) {
		var sql = " select * from publication where payload = ? and payload_class = ? ";
		var jsonPublicationKey = JsonUtils.write(publicationKey);
		return this.db//
			.sql(sql)//
			.params(jsonPublicationKey, clazz.getName())//
			.query(this.publicationRowMapper)//
			.stream() //
			.sorted(Comparator.comparing(Publication::created).reversed()) //
			.toList();
	}

}
