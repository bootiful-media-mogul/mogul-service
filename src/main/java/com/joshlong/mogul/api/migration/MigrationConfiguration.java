package com.joshlong.mogul.api.migration;

import com.joshlong.mogul.api.managedfiles.Storage;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.client.HttpSyncGraphQlClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.JettyClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Configuration
class MigrationConfiguration {

	private static final String API_ROOT = "http://127.0.0.1:8080";

	static final AtomicReference<String> TOKEN = new AtomicReference<>();

	private final JdbcClient oldDb, newDb;

	MigrationConfiguration(DataSource dataSource, @Value("${legacy.db.username:mogul}") String username,
			@Value("${legacy.db.password:mogul}") String password, @Value("${legacy.db.host:localhost}") String host,
			@Value("${legacy.db.schema:legacy}") String schema) {
		this.oldDb = JdbcClient.create(dataSource(username, password, host, schema));
		this.newDb = JdbcClient.create(dataSource);
	}

	@Bean
	Migration migration(Storage storage, ApplicationEventPublisher publisher, OldApiClient oldApiClient,
			NewApiClient newApiClient) {
		return new Migration(storage, this.oldDb, this.newDb, publisher, oldApiClient, newApiClient);
	}

	static final String MIGRATION_REST_CLIENT_QUALIFIER = "migrationRestClient";

	static final String MIGRATION_REST_TEMPLATE_QUALIFIER = "migrationRestTemplate";

	@Bean(MIGRATION_REST_TEMPLATE_QUALIFIER)
	RestTemplate restTemplate(RestTemplateBuilder builder) {
		return builder.rootUri(API_ROOT)
			.requestCustomizers(request -> request.getHeaders().putAll(authorizationHeader()))
			.requestFactory(JdkClientHttpRequestFactory.class)
			.build();
	}

	private static Map<String, List<String>> authorizationHeader() {
		return Map.of(HttpHeaders.AUTHORIZATION, List.of("Bearer " + TOKEN.get()));
	}

	@Bean(MIGRATION_REST_CLIENT_QUALIFIER)
	RestClient migrationRestClient(RestClient.Builder builder) {
		return builder.requestInitializer(request -> request.getHeaders().putAll(authorizationHeader()))
			.baseUrl(API_ROOT + "/graphql")
			.build();
	}

	@Bean
	HttpSyncGraphQlClient httpSyncGraphQlClient(
			@Qualifier(MIGRATION_REST_CLIENT_QUALIFIER) RestClient migrationRestClient) {
		return HttpSyncGraphQlClient.create(migrationRestClient);
	}

	@Bean
	NewApiClient newApiClient(HttpSyncGraphQlClient httpSyncGraphQlClient, JdbcClient jdbcClient,
			@Qualifier(MIGRATION_REST_CLIENT_QUALIFIER) RestClient restClient,
			@Qualifier(MIGRATION_REST_TEMPLATE_QUALIFIER) RestTemplate restTemplate) {
		return new NewApiClient(httpSyncGraphQlClient, jdbcClient, restClient, restTemplate);
	}

	@Bean
	OldApiClient oldApiClient() {
		return new OldApiClient(this.oldDb);
	}

	private static DataSource dataSource(String username, String password, String host, String dbSchema) {
		var jdbc = new JdbcConnectionDetails() {
			@Override
			public String getUsername() {
				return username;
			}

			@Override
			public String getPassword() {
				return password;
			}

			@Override
			public String getJdbcUrl() {
				return "jdbc:postgresql://" + host + "/" + dbSchema;
			}
		};
		return DataSourceBuilder //
			.create(Migration.class.getClassLoader())
			.type(HikariDataSource.class)
			.driverClassName(jdbc.getDriverClassName())
			.url(jdbc.getJdbcUrl())
			.username(jdbc.getUsername())
			.password(jdbc.getPassword())
			.build();
	}

}