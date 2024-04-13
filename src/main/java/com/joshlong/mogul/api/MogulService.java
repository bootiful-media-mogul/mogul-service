package com.joshlong.mogul.api;

import org.springframework.security.core.Authentication;

public interface MogulService {

	Mogul getCurrentMogul();

	Mogul login(Authentication principal);

	Mogul getMogulById(Long id);

	Mogul getMogulByName(String name);

	void assertAuthorizedMogul(Long aLong);

}
