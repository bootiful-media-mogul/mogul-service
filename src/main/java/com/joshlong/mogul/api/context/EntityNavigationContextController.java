package com.joshlong.mogul.api.context;

import com.joshlong.mogul.api.EntityContext;
import com.joshlong.mogul.api.EntityContextBuilder;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.utils.JsonUtils;
import com.joshlong.mogul.api.utils.ReflectionUtils;
import com.joshlong.mogul.api.utils.TypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * given <code>type</code> and <code>id</code>, we can return enough context for the
 * client to formulate a URL to load the right view to edit an entity as discovered in the
 * search results.
 */

@Controller
class EntityNavigationContextController {

	private final Logger log = LoggerFactory.getLogger(this.getClass());

	private final Map<String, EntityContextBuilder<?>> contextBuildersByType = new ConcurrentHashMap<>();

	private final MogulService mogulService;

	EntityNavigationContextController(EntityContextBuilder<?>[] entityContextBuilder, MogulService mogulService) {
		for (var cb : entityContextBuilder) {
			var generics = ReflectionUtils.genericsFor(cb.getClass());
			this.contextBuildersByType.put(TypeUtils.typeName(generics.iterator().next()), cb);
		}
		this.log.info(this.contextBuildersByType.toString());
		this.mogulService = mogulService;
	}

	@QueryMapping
	EntityContext entityContext(@Argument String type, @Argument Long id) {
		var mogul = mogulService.getCurrentMogul();
		this.log.info("resolve entityContext for type {} and id {}", type, id);
		var contextBuilder = this.contextBuildersByType.get(type);
		return contextBuilder.buildContextFor(mogul.id(), id);
	}

	@SchemaMapping
	String context(EntityContext navigationEntityContext) {
		return JsonUtils.write(navigationEntityContext.context());
	}

}
