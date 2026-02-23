package com.joshlong.mogul.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.Map;

@Controller
@ResponseBody
class IndexController {

	@GetMapping("/me")
	Map<String, String> me(Principal principal) {
		return Map.of("name", principal.getName());
	}

	@GetMapping("/")
	Map<String, String> hello(Principal principal) {
		return me(principal);
	}

	@GetMapping("/wp")
	Map<String, String> wp(HttpServletRequest request) {
		return Map.of("name", request.getHeader("X-WordPress-Token"));
	}

}
