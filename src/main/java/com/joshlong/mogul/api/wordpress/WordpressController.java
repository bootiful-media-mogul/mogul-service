package com.joshlong.mogul.api.wordpress;

import graphql.GraphQLError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Controller
class WordpressController {

    static final String WORDPRESS_TOKEN_CONTEXT_KEY = "wordpress-token";

    private final RestClient wordPressRestClient;

    WordpressController(@Qualifier(WordpressGraphqlConfiguration.WORDPRESS_REST_CLIENT) RestClient wordPressRestClient) {
        this.wordPressRestClient = wordPressRestClient;
    }

    @QueryMapping
    WordPressStatus wordPressStatus(@ContextValue(value = WORDPRESS_TOKEN_CONTEXT_KEY, required = false) String wpToken) {
        IO.println("wpToken is " + wpToken);
        WordPressTokenHolder.setToken(wpToken);
        if (wpToken != null) {
            var status = this.wordPressRestClient //
                    .get() //
                    .uri("/me") //
                    .retrieve() //
                    .body(Map.class);
            return new WordPressStatus((String) status.get("avatar_URL"), true,
                    (String) status.get("email"), (String) status.get("display_name"));
        }
        return new WordPressStatus(null, false, null, null);
    }
}

record WordPressStatus(
        String avatar_URL,
        boolean connected, String email,
        String displayName) {
}

class WordPressNotConnectedException extends RuntimeException {
}

@ControllerAdvice
class GraphQlExceptionAdvice {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @GraphQlExceptionHandler
    GraphQLError handleWordPressNotConnected(
            WordPressNotConnectedException ex) {
        this.log.warn("WordPress account not connected!", ex);
        return GraphQLError
                .newError() //
                .errorType(ErrorType.FORBIDDEN) //
                .message("WordPress account not connected") //
                .extensions(Map.of(
                        "code", "WORDPRESS_NOT_CONNECTED",
                        "action", "connect_wordpress"
                )) //
                .build();
    }
}

@Component
class WordPressTokenFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {
        try {
            var wpToken = request.getHeader("X-WordPress-Token");
            if (wpToken != null) {
                WordPressTokenHolder.setToken(wpToken);
            }
            chain.doFilter(request, response);
        } finally {
            WordPressTokenHolder.clear();
        }
    }
}

class WordPressTokenHolder {

    private static final ThreadLocal<String> token = new ThreadLocal<>();

    public static String getToken() {
        return token.get();
    }

    public static void setToken(String value) {
        token.set(value);
    }

    public static void clear() {
        token.remove();
    }
}

@Configuration
class WordpressGraphqlConfiguration {

    static final String WORDPRESS_REST_CLIENT = "wordpressRestClient";


    @Bean(WORDPRESS_REST_CLIENT)
    RestClient wordPressRestClient() {
        return RestClient.builder()
                .baseUrl("https://public-api.wordpress.com/rest/v1.1")
                .requestInterceptor((request, body, execution) -> {
                    var token = WordPressTokenHolder.getToken();
                    if (token != null) {
                        IO.println("setting WP bearer token to " + token);
                        request.getHeaders().setBearerAuth(token);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    @Bean
    WebGraphQlInterceptor headerInterceptor() {
        return (request, chain) -> {
            var wpToken = request.getHeaders().getFirst("X-WordPress-Token");
            request.configureExecutionInput((input, builder) ->
                    builder.graphQLContext(ctx -> {
                        if (wpToken != null) {
                            ctx.put(WordpressController.WORDPRESS_TOKEN_CONTEXT_KEY, wpToken);
                        }
                    }).build()
            );
            return chain.next(request);
        };
    }
}