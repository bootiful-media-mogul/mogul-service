package com.joshlong.mogul.api.blogs.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Map;
import java.util.Set;

@Component
class MarkdownDocuments {

	private final Logger log = LoggerFactory.getLogger(getClass());

	MarkdownDocument parse(String content) {
		var headerMap = this.headerMapFromString(content);
		var markdownDocumentHeader = this.markdownDocumentHeaderFromFromMap(headerMap);
		var body = this.body(content);
		return new MarkdownDocument(markdownDocumentHeader, body);
	}

	private String trimmedStringFromObject(Object hv) {
		if (hv == null) {
			return null;
		}
		if (hv instanceof String string)
			return string;
		return hv.toString();
	}

	@SuppressWarnings("unchecked")
	private <T> T valueForKey(Map<String, Object> map, String key) {
		if (map != null && map.containsKey(key)) {
			return (T) map.get(key);
		}
		return null;
	}

	private Date extractPossiblePublishedDate(Map<String, Object> map) {
		var popularDateNames = Set.of("publishedAt", "pubDate", "date");
		for (var k : popularDateNames) {
			var possibleMatch = (Date) valueForKey(map, k);
			if (possibleMatch != null)
				return possibleMatch;
		}
		return null;
	}

	private OffsetDateTime extractPublishedDate(String value, DateTimeFormatter formatter) {
		this.log.info("going to parse {}", value);
		var temporal = formatter.parseBest(value, OffsetDateTime::from, // full datetime
				// with offset
				LocalDateTime::from, // datetime, no offset
				LocalDate::from // date only
		);
		return switch (temporal) {
			case OffsetDateTime odt -> odt;
			case LocalDateTime ldt -> ldt.atOffset(ZoneOffset.UTC);
			case LocalDate ld -> ld.atStartOfDay().atOffset(ZoneOffset.UTC);
			default -> throw new DateTimeParseException("Unparseable date", value, 0);
		};
	}

	private MarkdownDocumentHeader markdownDocumentHeaderFromFromMap(Map<String, Object> rawHeader) {
		var publishedAt = this.extractPossiblePublishedDate(rawHeader);
		return new MarkdownDocumentHeader(rawHeader, //
				this.valueForKey(rawHeader, "author"), //
				this.valueForKey(rawHeader, "title"), //
				this.valueForKey(rawHeader, "category"), //
				publishedAt //
		);
	}

	private Map<String, Object> headerMapFromString(String content) {
		if (!content.startsWith("---"))
			return Map.of();

		var end = content.indexOf("---", 3);
		if (end == -1)
			return Map.of();

		var yaml = content.substring(3, end).trim();
		return new Yaml().load(yaml);
	}

	private String body(String content) {
		if (!content.startsWith("---"))
			return content;
		var end = content.indexOf("---", 3);
		return end == -1 ? content : content.substring(end + 3).trim();
	}

}

record MarkdownDocumentHeader(Map<String, Object> rawHeader, String author, String title, String category,
		Date publishedAt) {
}

record MarkdownDocument(MarkdownDocumentHeader header, String body) {
}
