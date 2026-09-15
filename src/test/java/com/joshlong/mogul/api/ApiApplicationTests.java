package com.joshlong.mogul.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

@SpringBootTest
class ApiApplicationTests {

	@Test
	void contextLoads() {
		var am = ApplicationModules.of(ApiApplication.class);
		am.verify();
		IO.println(am);
		new Documenter(am).writeDocumentation();
	}

}
