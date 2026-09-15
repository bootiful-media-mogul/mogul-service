package com.joshlong.mogul.api.ayrshare;

public class AyrshareConstants {

	// todo this will result in configuration for <em>two</em> Ayrshare plugins.
	// which makes no sense. we need some way to have one set of configuration
	public static final String PODCAST_EPISODE_AYRSHARE_PLUGIN_NAME = "podcastEpisodeAyrshare";

	public static final String BLOG_POST_AYRSHARE_PLUGIN_NAME = "blogPostAyrshare";

	// the value is load-bearing and stays "mogulAyrshare" even though the plugin now
	// publishes a MogulStatus: it is the settings category key and the name the
	// client asks for by hand.
	public static final String MOGUL_STATUS_AYRSHARE_PLUGIN_NAME = "mogulAyrshare";

	public static final String API_KEY_SETTING_KEY = "ayrshareKey";

	public static final String TWITTER_OAUTH1_API_KEY = "twitterOauth1ApiKey";

	public static final String TWITTER_OAUTH1_API_SECRET = "twitterOauth1ApiSecret";

}
