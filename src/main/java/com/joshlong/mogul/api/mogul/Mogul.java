package com.joshlong.mogul.api.mogul;

import com.joshlong.mogul.api.Notable;

import java.util.Date;

/**
 * the main tenant/user of this system.
 */
public record Mogul(Long id, String username, String email, String clientId, String givenName, String familyName,
		Date updated) implements Notable {

	@Override
	public Long notableKey() {
		return this.id;
	}
}
