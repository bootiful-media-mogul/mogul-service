package com.joshlong.mogul.api;

import com.joshlong.mogul.api.mogul.MogulService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

@TestConfiguration
class TestSecurityConfiguration {

	@Bean
	UserDetailsService userDetailsService(MogulService mogulService) {
		return username -> {
			var mogul = mogulService.getMogulByName(username);
			return User.withUsername(mogul.username()).password("").roles("USER").build();
		};
	}

}

@SpringBootTest(classes = { ApiApplication.class, TestSecurityConfiguration.class },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PodcastIntegrationTest {

	static final String USER = "google-oauth2|107746898487618710317";

	private final Logger log = LoggerFactory.getLogger(getClass());

	private Long mogulId(HttpGraphQlTester tester) {
		var me = tester//
			.document(" query { me { name, email, givenName, id, familyName } } ")//
			.execute() //
			.path("me")//
			.entity(Map.class) //
			.get();
		Assertions.assertEquals("Josh", me.get("givenName"));
		Assertions.assertEquals("Long", me.get("familyName"));
		Assertions.assertEquals(USER, me.get("name"));
		return ((Number) me.get("id")).longValue();
	}

	private Long id(Map<?, ?> map) {
		if (map != null && map.containsKey("id")) {
			return ((Number) map.get("id")).longValue();
		}
		return null;
	}

	// todo make this work!!
	@Disabled
	@Test
	@WithUserDetails(USER)
	void podcastE2eTest(@Autowired WebApplicationContext applicationContext,
			@Autowired TransactionTemplate transactionTemplate, @Autowired MogulService mogulService) throws Exception {

		var mogulId = transactionTemplate.execute(_ -> {
			var login = mogulService.getMogulByName(USER);
			Assertions.assertNotNull(login, "the login should not be null");
			return login.id();
		});

		this.log.info("the mogulId is {}", mogulId);

		var client = MockMvcWebTestClient.bindToApplicationContext(applicationContext)
			.configureClient()
			.baseUrl("/graphql")
			.build();

		var tester = HttpGraphQlTester.create(client);

		Assertions.assertEquals(mogulId, this.mogulId(tester),
				"the mogulId returned from the API must match what we've loaded here locally");

		var podcast = tester //
			.document("mutation($title:String!){ createPodcast(title:$title){ id } }") //
			.variable("title", "Test Podcast")//
			.execute() //
			.path("createPodcast") //
			.entity(Map.class) //
			.get();

		var podcastId = this.id(podcast);
		this.log.info("the podcastId is {}", podcastId);

		// 2) Create episode draft
		var episode = tester
			.document("mutation($pid:Int!,$title:String!,$desc:String!){ "
					+ "createPodcastEpisodeDraft(podcastId:$pid,title:$title,description:$desc){ id } }")
			.variable("pid", podcastId)
			.variable("title", "Test Episode")
			.variable("desc", "Test Description")
			.execute()
			.path("createPodcastEpisodeDraft")
			.entity(Map.class)
			.get();
		var episodeId = this.id(episode);
		this.log.info("the episodeId is {}", episodeId);

		// 3) Create segment
		var segmentId = tester.document("mutation($eid:Int!){ createPodcastEpisodeSegment(podcastEpisodeId:$eid) }")
			.variable("eid", episodeId)
			.execute()
			.path("createPodcastEpisodeSegment")
			.entity(Integer.class)
			.get();

	}

	void endToEndPodcastTranscriptFlow(@LocalServerPort int port, @Autowired WebTestClient.Builder builder)
			throws Exception {
		var webTestClient = builder.build();
		var graphQlTester = HttpGraphQlTester.builder(builder).url("/api/graphql").build();

		// 1) Create podcast
		var podcastId = graphQlTester.document("mutation($title:String!){ createPodcast(title:$title){ id } }")
			.variable("title", "Test Podcast")
			.execute()
			.path("createPodcast.id")
			.entity(Integer.class)
			.get();

		var episodeId = graphQlTester
			.document("mutation($pid:Int!,$title:String!,$desc:String!){ "
					+ "createPodcastEpisodeDraft(podcastId:$pid,title:$title,description:$desc){ id } }")
			.variable("pid", podcastId)
			.variable("title", "Test Episode")
			.variable("desc", "Test Description")
			.execute()
			.path("createPodcastEpisodeDraft.id")
			.entity(Integer.class)
			.get();

		// 3) Create segment
		var segmentId = graphQlTester
			.document("mutation($eid:Int!){ createPodcastEpisodeSegment(podcastEpisodeId:$eid) }")
			.variable("eid", episodeId)
			.execute()
			.path("createPodcastEpisodeSegment")
			.entity(Integer.class)
			.get();

		// 4) Fetch graphic + audio ManagedFile IDs
		var graphicFileId = graphQlTester
			.document("query($id:Int!){ podcastEpisodeById(podcastEpisodeId:$id){ graphic{ id } } }")
			.variable("id", episodeId)
			.execute()
			.path("podcastEpisodeById.graphic.id")
			.entity(Integer.class)
			.get();

		var audioFileId = graphQlTester
			.document("query($id:Int!){ podcastEpisodeById(podcastEpisodeId:$id){ segments{ audio{ id } }}}")
			.variable("id", episodeId)
			.execute()
			.path("podcastEpisodeById.segments[0].audio.id")
			.entity(Integer.class)
			.get();

		// 5) Upload files via REST
		var graphicContent = Files.readAllBytes(Paths.get("src/test/resources/test.jpg"));
		var audioContent = Files.readAllBytes(Paths.get("src/test/resources/test.mp3"));

		webTestClient.post()
			.uri("/managedfiles/{id}", graphicFileId)
			.bodyValue(graphicContent)
			.header("Content-Type", "multipart/form-data")
			.exchange()
			.expectStatus()
			.is2xxSuccessful();

		webTestClient.post()
			.uri("/managedfiles/{id}", audioFileId)
			.bodyValue(audioContent)
			.header("Content-Type", "multipart/form-data")
			.exchange()
			.expectStatus()
			.is2xxSuccessful();

		// 6) Poll up to 30s (every 2s) until transcript appears
		String transcriptText = null;
		long start = System.currentTimeMillis();
		do {
			transcriptText = graphQlTester
				.document("query($ep:Int!){ podcastEpisodeById(podcastEpisodeId:$ep){"
						+ " segments { id transcript { transcript } } }}")
				.variable("ep", episodeId)
				.execute()
				.path("podcastEpisodeById.segments[0].transcript.transcript")
				.entity(String.class)
				.get();

			if (transcriptText != null && !transcriptText.isBlank()) {
				break;
			}
			Thread.sleep(2000L);
		}
		while (System.currentTimeMillis() - start < 30_000L);

		assert transcriptText != null && !transcriptText.isBlank()
				: "Expected transcript text to be present for segment " + segmentId;
	}

}
