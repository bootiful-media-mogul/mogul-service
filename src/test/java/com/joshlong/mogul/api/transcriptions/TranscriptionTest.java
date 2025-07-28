package com.joshlong.mogul.api.transcriptions;

import com.joshlong.mogul.api.Transcription;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.podcasts.PodcastService;
import com.joshlong.mogul.api.transcription.TranscriptionService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.transaction.support.TransactionTemplate;

import static com.joshlong.mogul.api.transcriptions.TranscriptionTestSecurityConfiguration.ONE;
import static org.junit.jupiter.api.Assertions.*;

@TestConfiguration
class TranscriptionTestSecurityConfiguration {

	static final String ONE = "user1";

	@Bean
	UserDetailsService userDetailsService() {
		var user1 = User.withUsername(ONE).password("pw").roles("USER").build();
		return new InMemoryUserDetailsManager(user1);
	}

}

@SpringBootTest
@Import(TranscriptionTestSecurityConfiguration.class)
class TranscriptionTest {

	private final Logger log = LoggerFactory.getLogger(getClass());

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
		var transcription = transcriptionService.transcribe(segment);

		this.log.debug("transcription: {}", transcription);

		// todo figure out why this is returning null!!

		// var descriptionComp =
		// podcastService.getPodcastEpisodeDescriptionComposition(episode.id());
		// var titleComp = podcastService.getPodcastEpisodeTitleComposition(episode.id());
		// assertNotNull(descriptionComp, "the composition for the description must be
		// non-null");
		// assertNotNull(titleComp, "the composition for the title must be non-null");
		//
		// assertTrue(descriptionComp.attachments().isEmpty(), "there should be no
		// attachments for the description, yet");
		//
		// var attachment = compositionService.createCompositionAttachment(mogulId,
		// descriptionComp.id(),
		// "this is the nicest image that's ever been attached, ever");
		// assertNotNull(attachment, "the attachment should not be null");
		// descriptionComp =
		// podcastService.getPodcastEpisodeDescriptionComposition(episode.id());
		// assertEquals(1, descriptionComp.attachments().size(), "there should be one
		// attachment for the title");
		//
		// compositionService.deleteCompositionAttachment(descriptionComp.attachments().iterator().next().id());
		// descriptionComp =
		// podcastService.getPodcastEpisodeDescriptionComposition(episode.id());
		// assertTrue(descriptionComp.attachments().isEmpty(), "there should be no
		// attachments for the description, yet");

	}

}