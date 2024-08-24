package com.joshlong.mogul.api.mogul;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.joshlong.mogul.api.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@RegisterReflectionForBinding(DefaultMogulService.MogulJwtAuthenticationTokenDetails.class)
class DefaultMogulService implements MogulService {

	private final Map<String, Mogul> mogulsByName = new ConcurrentHashMap<>();

	private final Map<Long, Mogul> mogulsById = new ConcurrentHashMap<>();

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final JdbcClient db;

	private final ApplicationEventPublisher publisher;

	private final MogulRowMapper mogulRowMapper = new MogulRowMapper();

	DefaultMogulService(JdbcClient jdbcClient, ApplicationEventPublisher publisher) {
		this.db = jdbcClient;
		this.publisher = publisher;
		Assert.notNull(this.db, "the db is null");
		try (var scheduledExecutorService = Executors.newScheduledThreadPool(1);) {
			scheduledExecutorService.scheduleAtFixedRate(() -> {
				mogulsById.clear();
				mogulsByName.clear();
				log.debug("mogul cache eviction...");
			}, 1, 5, TimeUnit.MINUTES);
		}
	}

	@Override
	public Mogul getCurrentMogul() {
		var name = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication().getName();
		return this.getMogulByName(name);
	}

	// todo fgure out how to make sure i read the values in the http request associated
	// with the info about the user
	// todo and then make sure to write it to the sql db. right now i dont think its even
	// reaching the upsert. class cast issues?

	@Override
	public Mogul login(Authentication principal) {
		var principalName = principal.getName();
		var mogulByName = (Mogul) null;
		if ((mogulByName = this.getMogulByName(principalName)) == null) {
			if (principal instanceof MogulJwtAuthenticationToken mogulJwtAuthenticationToken
					&& principal.getPrincipal() instanceof Jwt jwt && jwt.getClaims().get("aud") instanceof List list
					&& list.getFirst() instanceof String aud) {
				var sql = """
						insert into mogul(username,  client_id , email, given_name, family_name) values (?, ?,?, ?,?)
						on conflict on constraint mogul_client_id_username_key do  nothing
						""";
				var mogulJwtAuthenticationTokenDetails = mogulJwtAuthenticationToken.details();
				this.db.sql(sql)
					.params(principalName, //
							aud, //
							mogulJwtAuthenticationTokenDetails.email(), //
							mogulJwtAuthenticationTokenDetails.givenName(), //
							mogulJwtAuthenticationTokenDetails.familyName() //
					)//
					.update();
			} //
			else {
				log.debug("not a valid authentication!");
			}
			mogulByName = this.getMogulByName(principalName);
			this.publisher.publishEvent(new MogulCreatedEvent(mogulByName));
		}

		this.publisher.publishEvent(new MogulAuthenticatedEvent(mogulByName));
		// todo publish some sort of MogulAuthenticatedEvent so that we can use that as a
		// cue in the PodcastService to load the
		// particular mogul's data into memory.
		return mogulByName;
	}

	@Override
	public Mogul getMogulById(Long id) {
		var msg = new StringBuilder();
		msg.append("trying to resolve mogul by id " + id + "");
		var res = this.mogulsById.computeIfAbsent(id, mogulId -> {
			var mogul = this.db.sql("select * from mogul where id =? ").param(id).query(this.mogulRowMapper).single();
			msg.append(", cache missed, resolving by db query [").append(mogulId).append("]");
			return mogul;
		});
		if (log.isDebugEnabled()) {
			log.debug(msg.toString());
		}
		return res;
	}

	@Override
	public Mogul getMogulByName(String name) {
		var msg = new StringBuilder();
		msg.append("trying to resolve mogul by name [").append(name).append("]");
		var res = this.mogulsByName.computeIfAbsent(name, key -> {
			var moguls = this.db//
				.sql("select * from mogul where  username = ? ")
				.param(key)
				.query(this.mogulRowMapper)
				.list();
			Assert.state(moguls.size() <= 1, "there should only be one mogul with this username [" + name + "]");
			var mogul = moguls.isEmpty() ? null : moguls.getFirst();
			msg.append(", but had to hit the DB to find a mogul by name [").append(name).append("]");
			return mogul;
		});
		if (log.isDebugEnabled())
			log.debug(msg.toString());
		return res;
	}

	@Override
	public void assertAuthorizedMogul(Long mogulId) {
		var currentlyAuthenticated = this.getCurrentMogul();
		Assert.state(currentlyAuthenticated != null && currentlyAuthenticated.id().equals(mogulId),
				"the requested mogul [" + mogulId + "] is not currently authenticated");
	}

	private static class MogulRowMapper implements RowMapper<Mogul> {

		@Override
		public Mogul mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new Mogul(rs.getLong("id"), rs.getString("username"), rs.getString("email"),
					rs.getString("client_id"), rs.getString("given_name"), rs.getString("family_name"));
		}

	}

	///

	@EventListener
	void authenticationSuccessEvent(AuthenticationSuccessEvent ase) {
		var requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		var request = Objects.requireNonNull(requestAttributes).getRequest();
		var headerValue = request.getHeader("X-Auth-Details");
		Assert.state(StringUtils.hasText(headerValue),
				"there is supposed to be enriching data about the mogul in the request.");
		var details = JsonUtils.read(headerValue, MogulJwtAuthenticationTokenDetails.class);
		var authentication = new MogulJwtAuthenticationToken((JwtAuthenticationToken) ase.getAuthentication(), details);
		// todo change the context
		SecurityContextHolder.getContext().setAuthentication(authentication);
		this.login(authentication);
	}

	static class MogulJwtAuthenticationToken extends JwtAuthenticationToken {

		private final MogulJwtAuthenticationTokenDetails details;

		MogulJwtAuthenticationTokenDetails details() {
			return details;
		}

		public MogulJwtAuthenticationToken(JwtAuthenticationToken delegate,
				MogulJwtAuthenticationTokenDetails details) {
			super(delegate.getToken(), delegate.getAuthorities(), delegate.getName());
			this.details = details;
		}

	}

	record MogulJwtAuthenticationTokenDetails(@JsonProperty("family_name") String familyName,
			@JsonProperty("given_name") String givenName, String picture, String email) {
	}

}
