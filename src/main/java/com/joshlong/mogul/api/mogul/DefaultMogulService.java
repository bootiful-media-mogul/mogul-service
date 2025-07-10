package com.joshlong.mogul.api.mogul;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.joshlong.mogul.api.utils.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Transactional
@ImportRuntimeHints(DefaultMogulService.Hints.class)
class DefaultMogulService implements MogulService {

	private final RestClient userinfoHttpRestClient = RestClient.builder().build();

	private final int maxEntries = 100;

	private final Duration tenMins = Duration.ofMinutes(10);

	private final Map<Long, Mogul> mogulsById = CollectionUtils.evictingConcurrentMap(this.maxEntries, this.tenMins);

	private final Map<String, Mogul> mogulsByName = CollectionUtils.evictingConcurrentMap(this.maxEntries,
			this.tenMins);

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final JdbcClient db;

	private final ApplicationEventPublisher publisher;

	private final MogulRowMapper mogulRowMapper = new MogulRowMapper();

	private final TransactionTemplate transactionTemplate;

	private final String auth0Userinfo;

	DefaultMogulService(@Value("${auth0.userinfo}") String auth0Userinfo, JdbcClient jdbcClient,
			ApplicationEventPublisher publisher, TransactionTemplate transactionTemplate) {
		this.auth0Userinfo = auth0Userinfo;
		this.transactionTemplate = transactionTemplate;
		this.db = jdbcClient;
		this.publisher = publisher;
		Assert.notNull(this.db, "the db is null");
	}

	@Override
	public Mogul getCurrentMogul() {
		var name = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication().getName();
		return this.getMogulByName(name);
	}

	@Override
	public Mogul login(String username, String clientId, String email, String first, String last) {
		this.log.debug("logging in mogul [{}] with client id [{}] and email [{}]", username, clientId, email);
		var mogulByName = (Mogul) null;
		if ((mogulByName = this.getMogulByName(username)) == null) {
			var sql = """
					insert into mogul(username,  client_id , email, given_name, family_name,updated) values (?, ?,?, ?,?,NOW())
					on conflict on constraint mogul_client_id_username_key do  update set updated = NOW()
					""";
			this.db.sql(sql)
				.params(username, //
						clientId, //
						email, //
						first, //
						last //
				)//
				.update();

			mogulByName = this.getMogulByName(username);
			this.nonNullMogul(mogulByName, username);
			this.publisher.publishEvent(new MogulCreatedEvent(mogulByName));
		} //
		this.nonNullMogul(mogulByName, username);
		this.publisher.publishEvent(new MogulAuthenticatedEvent(mogulByName));
		return mogulByName;
	}

	private void nonNullMogul(Mogul mogul, String username) {
		Assert.notNull(mogul, "the mogul by name [" + username + "] is null");
	}

	/**
	 * adapts calls to {@link this#login(String, String, String, String, String)}
	 */
	private Mogul doLoginByPrincipal(JwtAuthenticationToken principal) {
		var username = principal.getName();
		this.log.trace("logging in mogul [{}] with client id [{}]", username, principal.getName());
		var mogul = this.getMogulByName(username);
		if (null == mogul) {
			if (principal.getPrincipal() instanceof Jwt jwt && jwt.getClaims().get("aud") instanceof List<?> list
					&& list.getFirst() instanceof String aud) {
				this.log.trace(
						"could NOT find a recent mogul by name [{}] in the database, so we'll have to hit the /userinfo endpoint.",
						username);
				var accessToken = principal.getToken().getTokenValue();
				var userinfo = this.userinfoHttpRestClient.get()//
					.uri(this.auth0Userinfo)//
					.headers(httpHeaders -> httpHeaders.setBearerAuth(accessToken))//
					.retrieve()//
					.body(UserInfo.class);
				mogul = this.login(userinfo.sub(), aud, userinfo.email(), userinfo.givenName(), userinfo.familyName());
			}
		}
		this.nonNullMogul(mogul, username);
		return mogul;
	}

	@Override
	public Mogul getMogulById(Long id) {
		var resolved = new AtomicBoolean(false);
		var res = this.mogulsById.computeIfAbsent(id, mogulId -> {
			var mogul = this.db.sql("select * from mogul where id =? ").param(id).query(this.mogulRowMapper).single();
			resolved.set(true);
			return mogul;
		});
		this.logMogulCacheAttempt(id, "id", resolved.get());
		return res;
	}

	@Override
	public Mogul getMogulByName(String name) {
		var resolved = new AtomicBoolean(false);
		var res = this.mogulsByName.computeIfAbsent(name, key -> {
			var moguls = this.db//
				.sql("select * from mogul where username = ? ")
				.param(key)
				.query(this.mogulRowMapper)
				.list();
			Assert.state(moguls.size() <= 1, "there should only be one mogul with this username [" + name + "]");
			var mogul = moguls.isEmpty() ? null : moguls.getFirst();
			resolved.set(true);
			return mogul;
		});
		this.logMogulCacheAttempt(name, "name", resolved.get());
		return res;
	}

	private void logMogulCacheAttempt(Object input, String type, boolean resolved) {
		this.log.trace("tried to resolve the mogul by {} with input [{}] and found it {}.", type, input,
				resolved ? "in the DB" : "in the cache");
	}

	@Override
	public void assertAuthorizedMogul(Long mogulId) {
		var currentlyAuthenticated = this.getCurrentMogul();
		Assert.state(currentlyAuthenticated != null && currentlyAuthenticated.id().equals(mogulId),
				"the requested mogul [" + mogulId + "] is not currently authenticated");
	}

	@EventListener
	void authenticationSuccessEvent(AuthenticationSuccessEvent ase) {
		this.log.trace("handling authentication success event for {}", ase.getAuthentication().getName());
		this.transactionTemplate.execute(status -> {
			var authentication = (JwtAuthenticationToken) ase.getAuthentication();
			this.doLoginByPrincipal(authentication);
			return null;
		});
	}

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			var values = MemberCategory.values();
			for (var c : Set.of(UserInfo.class)) {
				hints.reflection().registerType(c, values);
			}
		}

	}

	// just for the first time login
	private record UserInfo(String sub, @JsonProperty("username") String username,
			@JsonProperty("given_name") String givenName, @JsonProperty("family_name") String familyName,
			String nickname, String picture, String email) {

	}

	private static class MogulRowMapper implements RowMapper<Mogul> {

		@Override
		public Mogul mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new Mogul(rs.getLong("id"), rs.getString("username"), rs.getString("email"),
					rs.getString("client_id"), rs.getString("given_name"), rs.getString("family_name"),
					rs.getDate("updated"));
		}

	}

}
