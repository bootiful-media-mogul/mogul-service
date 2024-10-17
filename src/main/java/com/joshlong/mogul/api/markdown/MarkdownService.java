package com.joshlong.mogul.api.markdown;

/**
 * given some markup, render the Markdown equivalent and send it back to the client.
 */
interface MarkdownService {

	String render(String text);

}
