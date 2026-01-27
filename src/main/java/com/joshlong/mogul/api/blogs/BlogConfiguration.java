package com.joshlong.mogul.api.blogs;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.Configuration;

@Configuration
@RegisterReflectionForBinding({ Blog.class, Post.class })
class BlogConfiguration {

}
