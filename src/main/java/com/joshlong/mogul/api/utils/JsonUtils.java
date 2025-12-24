package com.joshlong.mogul.api.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Role;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.atomic.AtomicReference;

/**
 * have Spring create an instance of this and we'll capture the {@link ObjectMapper om} in
 * a static variable.
 */
@Component
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class JsonUtils {

	private static final AtomicReference<JsonMapper> OBJECT_MAPPER_ATOMIC_REFERENCE = new AtomicReference<>();

	JsonUtils(JsonMapper objectMapper) {
		OBJECT_MAPPER_ATOMIC_REFERENCE.set(objectMapper);
	}

	public static <T> T read(String json, ParameterizedTypeReference<T> ptr) {
		var tr = new TypeReference<T>() {
		};
		return OBJECT_MAPPER_ATOMIC_REFERENCE.get().readValue(json, tr);
	}

	public static <T> T read(String json, Class<T> ptr) {
		return OBJECT_MAPPER_ATOMIC_REFERENCE.get().readValue(json, ptr);
	}

	public static String write(Object o) {
		return OBJECT_MAPPER_ATOMIC_REFERENCE.get().writeValueAsString(o);
	}

}
