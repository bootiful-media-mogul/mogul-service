package com.joshlong.mogul.api.wordpress;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

public interface WordPressClient {

	WordPressPostResponse publishPost(WordPressPost post);

	WordPressPostResponse saveDraft(WordPressPost post);

	WordPressMediaResponse uploadMedia(String filename, Resource data, MediaType mimeType);

	WordPressPostResponse updatePost(int postId, WordPressPost post);

	WordPressStatus status();

}
