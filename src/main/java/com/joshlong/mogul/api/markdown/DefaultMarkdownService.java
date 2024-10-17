package com.joshlong.mogul.api.markdown;

import org.springframework.stereotype.Service;

@Service
class DefaultMarkdownService implements MarkdownService {

	private final com.joshlong.templates.MarkdownService markdownService;

	DefaultMarkdownService(com.joshlong.templates.MarkdownService markdownService) {
		this.markdownService = markdownService;
	}

	@Override
	public String render(String text) {
		return this.markdownService.convertMarkdownTemplateToHtml(text);
	}

}
