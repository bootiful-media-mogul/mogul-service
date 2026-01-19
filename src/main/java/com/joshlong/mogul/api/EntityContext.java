package com.joshlong.mogul.api;

import java.util.Map;

/**
 * sometimes we need to pass entity specific context to the client to help it build
 * navigation URLs
 */
public record EntityContext(String resolvedType, Map<String, Object> context) {
}