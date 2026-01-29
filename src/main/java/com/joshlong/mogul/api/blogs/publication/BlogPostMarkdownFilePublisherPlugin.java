package com.joshlong.mogul.api.blogs.publication;


import com.joshlong.mogul.api.PublisherPlugin;
import com.joshlong.mogul.api.blogs.BlogService;
import com.joshlong.mogul.api.blogs.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

import static com.joshlong.mogul.api.blogs.publication.BlogPostMarkdownFilePublisherPlugin.PLUGIN_NAME;

@Component(value = PLUGIN_NAME)
class BlogPostMarkdownFilePublisherPlugin implements PublisherPlugin<Post> {

    static final String PLUGIN_NAME = "blogPostMarkdownFile";

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final BlogService blogService;

    BlogPostMarkdownFilePublisherPlugin(BlogService blogService) {
        this.blogService = blogService;
    }

    @Override
    public String name() {
        return PLUGIN_NAME;
    }

    @Override
    public Set<String> requiredSettingKeys() {
        return Set.of();
    }

    @Override
    public void publish(PublishContext<Post> publishContext) {
        for (var entry : publishContext.context().entrySet()) {
            this.log.info("{}={}", entry.getKey(), entry.getValue());
        }
        this.log.info ("publishing Markdown file {} for post",
                publishContext.payload().id());
    }

    @Override
    public boolean unpublish(UnpublishContext<Post> uc) {
        return false;
    }
}
