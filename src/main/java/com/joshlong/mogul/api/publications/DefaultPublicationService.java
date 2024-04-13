package com.joshlong.mogul.api.publications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshlong.mogul.api.MogulService;
import com.joshlong.mogul.api.settings.Settings;
import com.joshlong.mogul.api.utils.JdbcUtils;
import com.joshlong.mogul.api.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
class DefaultPublicationService implements PublicationService {

	private final Map<String, PublisherPlugin<?>> plugins = new ConcurrentHashMap<>();

	private final JdbcClient db;

	private final Settings settings;

	private final MogulService mogulService;

	private final RowMapper<Publication> publicationRowMapper;

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final TextEncryptor textEncryptor;

	DefaultPublicationService(JdbcClient db, Settings settings, MogulService mogulService, TextEncryptor textEncryptor,
			Map<String, PublisherPlugin<?>> plugins, ObjectMapper objectMapper) {
		this.db = db;
		this.settings = settings;
		this.mogulService = mogulService;
		this.textEncryptor = textEncryptor;
		this.plugins.putAll(plugins);
		Assert.notNull(this.db, "the JdbcClient must not be null");
		Assert.notNull(this.mogulService, "the mogulService must not be null");
		Assert.notNull(this.textEncryptor, "the textEncryptor must not be null");
		Assert.notNull(this.settings, "the settings must not be null");
		Assert.state(!this.plugins.isEmpty(), "there are no plugins for publication");
		this.publicationRowMapper = new PublicationRowMapper(objectMapper, textEncryptor);
	}

	@Override
	public <T extends Publishable> Publication publish(Long mogulId, T payload, Map<String, String> contextAndSettings,
			PublisherPlugin<T> plugin) {
		var mogul = this.mogulService.getMogulById(mogulId);
		Assert.notNull(plugin, "the plugin must not be null");
		Assert.notNull(payload, "the payload must not be null");
		Assert.notNull(mogul, "the mogul should not be null");
		var configuration = this.settings.getAllValuesByCategory(this.mogulService.getCurrentMogul().id(),
				plugin.name());
		var context = new HashMap<String, String>();
		context.putAll(configuration);
		context.putAll(contextAndSettings);
		plugin.publish(context, payload);
		log.debug("finished publishing with plugin " + plugin.name() + '.');
		var contextJson = this.textEncryptor.encrypt(JsonUtils.write(context));
		var publicationData = this.textEncryptor.encrypt(JsonUtils.write(payload.publicationKey()));
		var entityClazz = payload.getClass().getName();
		var kh = new GeneratedKeyHolder();
		this.db.sql(
				"insert into publication(mogul_id, plugin, created, published, context, payload , payload_class) VALUES (?,?,?,?,?,?,?)")
			.params(mogulId, plugin.name(), new Date(), null, contextJson, publicationData, entityClazz)
			.update(kh);
		var publication = this.getPublicationById(JdbcUtils.getIdFromKeyHolder(kh).longValue());
		log.debug("writing publication out: " + publication);
		return publication;
	}

	@Override
	public Publication getPublicationById(Long publicationId) {
		return db.sql("select * from publication where id =? ")
			.params(publicationId)
			.query(this.publicationRowMapper)
			.single();
	}

}
