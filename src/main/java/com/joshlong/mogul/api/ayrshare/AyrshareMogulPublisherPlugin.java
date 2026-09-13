package com.joshlong.mogul.api.ayrshare;

import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.mogul.Mogul;
import com.joshlong.mogul.api.mogul.MogulPublisherPlugin;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.settings.Settings;
import org.springframework.stereotype.Component;

import static com.joshlong.mogul.api.ayrshare.AyrshareConstants.MOGUL_AYRSHARE_PLUGIN_NAME;

@Component(MOGUL_AYRSHARE_PLUGIN_NAME)
class AyrshareMogulPublisherPlugin extends AbstractAyrsharePublisherPlugin<Mogul> implements MogulPublisherPlugin {

	AyrshareMogulPublisherPlugin(AyrshareService ayrshare, Settings settings, MogulService mogulService,
			ManagedFileService managedFileService) {
		super(MOGUL_AYRSHARE_PLUGIN_NAME, ayrshare, settings, mogulService, managedFileService);
	}

}
