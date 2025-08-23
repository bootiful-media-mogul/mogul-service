package com.joshlong.mogul.api;

import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.utils.JsonUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.UrlResource;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.reactive.function.BodyInserters;

import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

	private static final long SLEEP_IN_MILLISECONDS = Duration.ofSeconds(10).toMillis();

	private final Logger log = LoggerFactory.getLogger(getClass());

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

		var builder = MockMvcWebTestClient.bindToApplicationContext(applicationContext).configureClient();
		var webTestClient = builder.build();
		var tester = HttpGraphQlTester.create(builder.baseUrl("/graphql").build());

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

		// we need to get the segment by its id so we can look at the managedfile for it.
		var graphicManagedFileId = tester
			.document("query($id:Int!){ podcastEpisodeById(podcastEpisodeId:$id){ graphic{ id } } }")
			.variable("id", episodeId)
			.execute()
			.path("podcastEpisodeById.graphic.id")
			.entity(Integer.class)
			.get();

		var segmentManagedFileId = tester
			.document("query($id:Int!){ podcastEpisodeById(podcastEpisodeId:$id){ segments{ audio{ id } }}}")
			.variable("id", episodeId)
			.execute()
			.path("podcastEpisodeById.segments[0].audio.id")
			.entity(Integer.class)
			.get();

		this.log.debug("graphicManagedFileId is {}", graphicManagedFileId);
		this.log.debug("segmentManagedFileId is {}", segmentManagedFileId);

		// 5) Upload files via REST endpoint.
		var mappings = Map.of( //
				segmentManagedFileId, new ClassPathResource("samples/sample-segment-1.mp3"), //
				graphicManagedFileId, new ClassPathResource("samples/sample-picture.png") //
		);

		for (var entry : mappings.entrySet()) {
			var mfId = entry.getKey();
			var resource = entry.getValue();
			Assertions.assertTrue(resource.exists(), "the resource [" + resource + "] must exist");
			webTestClient.post()
				.uri("/managedfiles/{id}", mfId)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(BodyInserters.fromMultipartData("file", resource))
				.exchange()
				.expectStatus()
				.is2xxSuccessful();
		}

		this.log.debug("all files uploaded");

		var ready = new AtomicBoolean(false);
		var started = System.currentTimeMillis();
		var finish = started + Duration.ofMinutes(10).toMillis(); // finish watching in 5
		// minutes
		while (!ready.get() && System.currentTimeMillis() < finish) {
			Thread.sleep(SLEEP_IN_MILLISECONDS);
			var transcript = tester //
				.document("""
						query($id:Int!){
						  podcastEpisodeById(podcastEpisodeId:$id){
						    complete,
						    id,
						    segments {
						        transcript { id, transcript },
						        id
						    }
						  }
						}
						""") //
				.variable("id", episodeId)
				.execute()
				.path("podcastEpisodeById")
				.entity(Map.class)
				.get();
			this.log.debug("episode is {}", JsonUtils.write(transcript));
			var complete = (Boolean) transcript.get("complete");
			var segments = (Collection<Map<String, Object>>) transcript.get("segments");
			for (var segment : segments) {
				var transcriptObject = (Map<String, Object>) segment.get("transcript");
				var transcriptText = (String) transcriptObject.get("transcript");
				this.log.debug("segment {} has transcript {}", segment.get("id"), transcriptText);
				if (transcriptText != null && !transcriptText.isEmpty() && complete) {
					ready.set(true);
				}
			}
		}

		Assertions.assertTrue(ready.get(), "the transcript should be ready by now");

		// now we publish the episode using the publication subsystem.
		tester.document("""
				mutation Publish($id: Int!) {
				  publish(
				    publishableId: $id,
				    publishableType: "episode",
				    contextJson: "{}",
				    plugin: "audioFile"
				  )
				}
				""").variable("id", episodeId).execute().path("publish").entity(Boolean.class).get();

		var publicationsForPublishableQuery = """
				 query (
				   $type: String,
				   $id: Int
				 ) {
				    publicationsForPublishable(
				      id: $id,
				      type : $type
				    ){
				       id,
				       plugin,
				       published,
				       url,
				       state,
				       outcomes  {
				         id,
				         success,
				         url,
				         key,
				         serverErrorMessage
				       }
				    }
				}
				""";

		var published = new AtomicReference<String>();
		var startPublications = System.currentTimeMillis();
		var stopPublicationsWaiting = startPublications + Duration.ofMinutes(10).toMillis();
		while (System.currentTimeMillis() < stopPublicationsWaiting && published.get() == null) {
			Thread.sleep(SLEEP_IN_MILLISECONDS);
			var publications = tester //
				.document(publicationsForPublishableQuery) //
				.variable("type", "episode")
				.variable("id", episodeId)
				.execute()
				.path("publicationsForPublishable")
				.entity(new ParameterizedTypeReference<List<Map<String, Object>>>() {
				})
				.get();
			this.log.debug("publications are {}", JsonUtils.write(publications));
			for (var p : publications) {
				if (p.containsKey("outcomes")) {
					var outcomes = (List<Map<String, Object>>) p.get("outcomes");
					for (var outcome : outcomes) {
						var success = (Boolean) outcome.get("success");
						var url = (String) outcome.get("url");
						if (success) {
							this.log.info("the publication outcome for {} was {}", p.get("id"), url);
							published.set(url);
						}
					}
				}
			}
		}

		this.log.debug("the published url is {}", published.get());

		Assertions.assertNotNull(published.get(), "there should be a published artifact");

		var remoteMp3Resource = new UrlResource(URI.create(published.get()));
		var localMp3Resource = new FileSystemResource(
				System.getProperty("java.io.tmpdir") + "/" + UUID.randomUUID() + "-podcast.mp3");
		var localMp3ResourceFile = localMp3Resource.getFile();
		try (var in = remoteMp3Resource.getInputStream(); var out = localMp3Resource.getOutputStream()) {
			FileCopyUtils.copy(in, out);
			Assertions.assertTrue(localMp3Resource.exists() && localMp3ResourceFile.length() > 0,
					"the file should be non-zero and exist.");
		} //
		finally {
			if (localMp3Resource.exists()) {
				try {
					localMp3ResourceFile.delete();
				} //
				catch (Throwable t) {
					this.log.debug("could not delete the local file at " + localMp3ResourceFile.getAbsolutePath(), t);
				}
			}
		}

		// X - search for the podcast episode and test that the search capabilities work.

		// X - add a note to the episode

	}

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

}
