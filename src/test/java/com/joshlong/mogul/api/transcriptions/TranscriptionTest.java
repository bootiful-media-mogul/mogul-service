package com.joshlong.mogul.api.transcriptions;

import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.podcasts.PodcastService;
import com.joshlong.mogul.api.transcription.TranscriptionService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.transaction.support.TransactionTemplate;

import static com.joshlong.mogul.api.transcriptions.TranscriptionTestSecurityConfiguration.ONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestConfiguration
class TranscriptionTestSecurityConfiguration {

	static final String ONE = "user1";

	@Bean
	UserDetailsService userDetailsService() {
		var user1 = User.withUsername(ONE).password("pw").roles("USER").build();
		return new InMemoryUserDetailsManager(user1);
	}

}

// todo restore this test!
@SpringBootTest
@Import(TranscriptionTestSecurityConfiguration.class)
class TranscriptionTest {

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Autowired
	private ManagedFileService managedFileService;

	@Disabled
	@Test
	@WithUserDetails(ONE)
	void transcription(@Autowired TranscriptionService transcriptionService, @Autowired PodcastService podcastService,
			@Autowired MogulService mogulService, @Autowired TransactionTemplate transactionTemplate) {
		var mogulId = transactionTemplate.execute(_ -> {
			var login = mogulService.login("username", ONE, "123", "Josh", "Long");
			assertNotNull(login, "the login should not be null");
			return login.id();
		});

		var podcast = podcastService.createPodcast(mogulId, "the simplest podcast ever");
		var episode = podcastService.createPodcastEpisodeDraft(mogulId, podcast.id(), "the title", "the description");
		assertEquals(episode.id(), episode.compositionKey());

		var segment = podcastService.createPodcastEpisodeSegment(mogulId, episode.id(), "segment", 0);
		var transcription = transcriptionService.transcription(mogulId, segment);

		var cpr = new ClassPathResource("/samples/2.aiff.mp3");
		managedFileService.write(segment.producedAudio().id(), cpr.getFilename(), CommonMediaTypes.MP3, cpr);
		// // todo does us writing this segment in turn result in the IntegrationFlow
		// kicking
		// // off for production?
		//
		// Awaitility.await().atLeast(Duration.ofMinutes(1)).untilAsserted(() -> {
		//
		// // todo i need to produce an episode. is the productionm tied to the
		// // publication? do iu have logic separate from that?
		// // todo also do we need the produced audio to handle the transcription? does
		// // that means there's an implicit dependency?
		// // todo should we write the code to instead have the transcription kicked off
		// // ONLY after the audio has been produced?
		//
		// });
		//
		// transcriptions.transcribe(segment);
		//
		// this.log.debug("transcription: {}", transcription);
		//
		// Awaitility.await().atLeast(Duration.ofMinutes(1)).untilAsserted(() -> {
		//
		// });

	}

}