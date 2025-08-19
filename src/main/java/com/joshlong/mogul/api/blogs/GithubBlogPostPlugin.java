package com.joshlong.mogul.api.blogs;

import com.joshlong.mogul.api.PublisherPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

// todo can we put a testConnection method or something that uses the credential provided to validate that the connection
//  will work? maybe call a rest api that requires no parameters?
@Component(GithubBlogPostPlugin.PLUGIN_NAME)
class GithubBlogPostPlugin implements PublisherPlugin<Post>, BeanNameAware {

	public static final String PLUGIN_NAME = "github";

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final AtomicReference<String> beanName = new AtomicReference<>();

	@Override
	public String name() {
		return this.beanName.get();
	}

	@Override
	public Set<String> requiredSettingKeys() {
		return Set.of("clientId", "clientSecret");
	}

	@Override
	public boolean isConfigurationValid(Map<String, String> context) {
		return context.containsKey("clientId") && context.containsKey("clientSecret");
	}

	@Override
	public boolean canPublish(PublishContext<Post> c) {
		return PublisherPlugin.super.canPublish(c);
	}

	@Override
	public void publish(PublishContext<Post> p) {

	}

	@Override
	public boolean unpublish(UnpublishContext<Post> context) {
		return false;
	}

	@Override
	public void setBeanName(@NonNull String name) {
		this.beanName.set(name);
	}

}
