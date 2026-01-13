package com.joshlong.mogul.api;

import com.joshlong.mogul.api.mogul.Mogul;
import com.joshlong.mogul.api.mogul.MogulService;
import org.springframework.stereotype.Component;

@Component
class MogulNotableResolver extends AbstractNotableResolver<Mogul> {

	private final MogulService mogulService;

	MogulNotableResolver(MogulService ms) {
		super(Mogul.class);
		this.mogulService = ms;
	}

	@Override
	public Mogul find(Long key) {
		return this.mogulService.getMogulById(key);
	}

}
