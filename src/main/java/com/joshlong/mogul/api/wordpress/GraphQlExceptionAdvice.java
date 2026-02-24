package com.joshlong.mogul.api.wordpress;

import graphql.GraphQLError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.Map;

@ControllerAdvice
class GraphQlExceptionAdvice {

	private final Logger log = LoggerFactory.getLogger(getClass());

	@GraphQlExceptionHandler
	GraphQLError handleWordPressNotConnected(WordPressNotConnectedException ex) {
		this.log.warn("WordPress account not connected!", ex);
		return GraphQLError.newError() //
			.errorType(ErrorType.FORBIDDEN) //
			.message("WordPress account not connected") //
			.extensions(Map.of("code", "WORDPRESS_NOT_CONNECTED", "action", "connect_wordpress")) //
			.build();
	}

}
