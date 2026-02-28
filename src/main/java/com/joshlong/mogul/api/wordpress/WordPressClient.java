package com.joshlong.mogul.api.wordpress;

public interface WordPressClient {

	WordPressPostResponse publishPost(WordPressPost post);

	WordPressStatus status();

}
