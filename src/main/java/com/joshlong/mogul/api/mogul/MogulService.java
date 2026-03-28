package com.joshlong.mogul.api.mogul;

import java.util.Collection;

public interface MogulService {

	Mogul getCurrentMogul();

	Mogul login(String username, String clientId, String email, String first, String last);

	Mogul getMogulById(Long id);

	Mogul getMogulByName(String name);

	Collection<Mogul> getMogulByEmail(String email);

	void assertAuthorizedMogul(Long aLong);

}
