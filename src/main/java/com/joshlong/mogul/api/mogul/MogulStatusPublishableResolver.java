package com.joshlong.mogul.api.mogul;

import com.joshlong.mogul.api.AbstractPublishableResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * resolves {@link com.joshlong.mogul.api.Publishable} instances of {@link MogulStatus},
 * so that a mogul's day -- and not the mogul -- can be the subject of a publication.
 */
@Component
class MogulStatusPublishableResolver extends AbstractPublishableResolver<MogulStatus> {

	private final MogulStatusService mogulStatusService;

	private final MogulService mogulService;

	MogulStatusPublishableResolver(MogulStatusService mogulStatusService, MogulService mogulService) {
		super(MogulStatus.class);
		this.mogulStatusService = mogulStatusService;
		this.mogulService = mogulService;
	}

	@Override
	public MogulStatus find(Long id) {
		var status = this.mogulStatusService.getMogulStatusById(id);
		Assert.notNull(status, "there is no mogul status with id [" + id + "]");
		this.mogulService.assertAuthorizedMogul(status.mogulId());
		return status;
	}

}
