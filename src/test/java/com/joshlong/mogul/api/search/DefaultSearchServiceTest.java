package com.joshlong.mogul.api.search;

import com.joshlong.mogul.api.ApiApplication;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

import java.nio.charset.Charset;

@SpringBootTest (classes = ApiApplication.class)
class DefaultSearchServiceTest {

    private final SearchService searchService;

    DefaultSearchServiceTest(@Autowired SearchService searchService) {
        this.searchService = searchService;
    }

    @Disabled
    @Test
    void index(@Value("classpath:/transcript.txt") Resource resource) throws Exception {
        var contentAsString = resource
                .getContentAsString(Charset.defaultCharset());
        this.searchService.ingest(1L, "Transcript", contentAsString);
        var results = this.searchService.search("Pivotal") ;
        results.forEach(System.out::println);
        System.out.println("found " + results.size() + " hits");
    }
}