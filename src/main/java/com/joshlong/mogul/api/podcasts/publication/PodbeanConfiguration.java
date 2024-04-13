package com.joshlong.mogul.api.podcasts.publication;

import com.joshlong.mogul.api.MogulService;
import com.joshlong.mogul.api.settings.Settings;
import com.joshlong.podbean.token.ClientCredentialsTokenProvider;
import com.joshlong.podbean.token.Token;
import com.joshlong.podbean.token.TokenProvider;
import org.aopalliance.intercept.MethodInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

/**
 * By default, the Podbean autoconfiguration creates a global and singular instance of
 * {@link TokenProvider tp} based on the configuration stipulated at design time.
 * <p>
 * This is a multi-tenant implementation that is aware of the currently signed in
 * {@link com.joshlong.mogul.api.Mogul }.
 */
@Configuration
class PodbeanConfiguration {

	@Bean
	TokenProvider multitenantTokenProvider(MogulService mogulService, Settings settings) {
		var tenantAwareTokenProvider = new MogulAwareTokenProvider(mogulService, settings);

		var tokenProviderClass = TokenProvider.class;
		var proxyFactory = new ProxyFactory();
		proxyFactory.setTargetClass(tokenProviderClass);
		proxyFactory.setInterfaces(tokenProviderClass.getInterfaces());
		proxyFactory.setProxyTargetClass(true);
		proxyFactory.addAdvice((MethodInterceptor) invocation -> {

			if (!invocation.getMethod().getName().equals("getToken"))
				return invocation.proceed();

			return tenantAwareTokenProvider.getToken();

		});
		return (TokenProvider) proxyFactory.getProxy();
	}

	static class MogulAwareTokenProvider implements TokenProvider {

		private final Logger log = LoggerFactory.getLogger(getClass());

		private final MogulService mogulService;

		private final Settings settings;

		MogulAwareTokenProvider(MogulService mogulService, Settings settings) {
			this.mogulService = mogulService;
			this.settings = settings;
		}

		@Override
		public Token getToken() {
			var currentMogul = mogulService.getCurrentMogul();
			var mogulId = currentMogul.id();
			var settingsForTenant = settings.getAllSettingsByCategory(mogulId,
					PodbeanPodcastEpisodePublisherPlugin.PLUGIN_NAME);
			var clientId = settingsForTenant.get("clientId").value();
			var clientSecret = settingsForTenant.get("clientSecret").value();
			Assert.hasText(clientId, "the podbean clientId for mogul [" + mogulId + "] is empty");
			Assert.hasText(clientSecret, "the podbean clientSecret for mogul [" + mogulId + "] is empty");
			log.debug("returning podbean " + ClientCredentialsTokenProvider.class.getName() + " for mogul ["
					+ currentMogul.username() + "]");
			return new ClientCredentialsTokenProvider(clientId, clientSecret).getToken();
		}

	}

}
