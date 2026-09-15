package com.joshlong.mogul.api.ayrshare;

import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.mogul.MogulStatus;
import com.joshlong.mogul.api.mogul.MogulStatusPublisherPlugin;
import com.joshlong.mogul.api.settings.Settings;
import org.springframework.stereotype.Component;

import static com.joshlong.mogul.api.ayrshare.AyrshareConstants.MOGUL_STATUS_AYRSHARE_PLUGIN_NAME;

@Component(MOGUL_STATUS_AYRSHARE_PLUGIN_NAME)
class AyrshareMogulStatusPublisherPlugin extends AbstractAyrsharePublisherPlugin<MogulStatus>
		implements MogulStatusPublisherPlugin {

	AyrshareMogulStatusPublisherPlugin(AyrshareService ayrshare, Settings settings, MogulService mogulService,
			ManagedFileService managedFileService) {
		super(MOGUL_STATUS_AYRSHARE_PLUGIN_NAME, ayrshare, settings, mogulService, managedFileService);
	}

}
