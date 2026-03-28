package com.joshlong.mogul.api.blogs.jobs;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
class MarkdownDocuments {

	MarkdownDocument parse(String content) {
		var headerMap = this.headerMapFromString(content);
		var markdownDocumentHeader = this.markdownDocumentHeaderFromFromMap(headerMap);
		var body = this.body(content);
		return new MarkdownDocument(markdownDocumentHeader, body);
	}

	private Yaml yaml() {
		// var rep = new Representer(new DumperOptions());
		// rep.setTimeZone(TimeZone.getTimeZone("UTC"));
		return new Yaml();
	}

	@SuppressWarnings("unchecked")
	private <T> T valueForKey(Map<String, Object> map, String key) {
		if (map != null && map.containsKey(key)) {
			return (T) map.get(key);
		}
		return null;
	}

	private LocalDate dateToLocalDate(Map<String, Object> map) {
		Date raw = null;
		var popularDateNames = Set.of("publishedAt", "pubDate", "date");
		for (var k : popularDateNames) {
			var possibleMatch = (Date) valueForKey(map, k);
			if (possibleMatch != null) {
				raw = possibleMatch;
				break;
			}
		}
		return Objects.requireNonNull(raw).toInstant().atZone(ZoneOffset.UTC).toLocalDate();
	}

	private MarkdownDocumentHeader markdownDocumentHeaderFromFromMap(Map<String, Object> rawHeader) {
		var localDate = this.dateToLocalDate(rawHeader);
		IO.println("published at: " + localDate);
		return new MarkdownDocumentHeader(rawHeader, //
				this.valueForKey(rawHeader, "author"), //
				this.valueForKey(rawHeader, "title"), //
				this.valueForKey(rawHeader, "category"), //
				localDate //
		);
	}

	private Map<String, Object> headerMapFromString(String content) {
		if (!content.startsWith("---"))
			return Map.of();

		var end = content.indexOf("---", 3);
		if (end == -1)
			return Map.of();

		var yaml = content.substring(3, end).trim();
		return this.yaml().load(yaml);
	}

	private String body(String content) {
		if (!content.startsWith("---"))
			return content;
		var end = content.indexOf("---", 3);
		return end == -1 ? content : content.substring(end + 3).trim();
	}

}

record MarkdownDocumentHeader(Map<String, Object> rawHeader, String author, String title, String category,
		LocalDate publishedAt) {
}

record MarkdownDocument(MarkdownDocumentHeader header, String body) {
}
