package com.joshlong.mogul.api.ayrshare;

import com.joshlong.mogul.api.compositions.CompositionService;
import com.joshlong.mogul.api.mogul.MogulService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

//@SpringBootTest(classes = ApiApplication.class)
class DefaultAyrshareServiceTest {

	private final CompositionService compositionService;

	private final MogulService mogulService;

	private final AyrshareService ayrshareService;

	DefaultAyrshareServiceTest(@Autowired CompositionService compositionService, @Autowired MogulService mogulService,
			@Autowired AyrshareService ayrshareService) {
		this.compositionService = compositionService;
		this.mogulService = mogulService;
		this.ayrshareService = ayrshareService;
	}

	// @Test
	void contextLoads() throws Exception {
		var mogul = this.mogulService.getMogulByName("google-oauth2|107746898487618710317");
		var draftAyrsharePublicationCompositionsFor = this.ayrshareService
			.getDraftAyrsharePublicationCompositionsFor(mogul.id());
		draftAyrsharePublicationCompositionsFor.forEach(System.out::println);
		Assertions.assertFalse(draftAyrsharePublicationCompositionsFor.isEmpty(),
				"there should be at least one draft ayrshare publication composition for " + mogul.givenName());
		Assertions.assertEquals(draftAyrsharePublicationCompositionsFor.size(), ayrshareService.platforms().length,
				"there should be one draft ayrshare publication composition for each platform");

	}

}