package com.joshlong.mogul.api.mogul;

import com.joshlong.mogul.api.AbstractPublishableResolver;
import org.springframework.stereotype.Component;

/**
 * resolves {@link com.joshlong.mogul.api.Publishable} instances of {@link Mogul}, so that
 * the mogul itself can be the subject of a publication.
 */
@Component
class MogulPublishableResolver extends AbstractPublishableResolver<Mogul> {

	private final MogulService mogulService;

	MogulPublishableResolver(MogulService mogulService) {
		super(Mogul.class);
		this.mogulService = mogulService;
	}

	@Override
	public Mogul find(Long id) {
		return this.mogulService.getMogulById(id);
	}

}
