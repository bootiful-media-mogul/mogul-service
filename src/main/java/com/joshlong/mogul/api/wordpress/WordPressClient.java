package com.joshlong.mogul.api.wordpress;

public interface WordPressClient {

	WordPressPostResponse publishPost(WordPressPost post);

	// WordPressPostResponse saveDraft(WordPressPost post);
	//
	// WordPressMediaResponse uploadMedia(String filename, Resource data, MediaType
	// mimeType);
	//
	// WordPressPostResponse updatePost(int postId, WordPressPost post);

	WordPressStatus status();

}
