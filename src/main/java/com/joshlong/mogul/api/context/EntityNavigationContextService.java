package com.joshlong.mogul.api.context;

import java.util.Map;

public interface EntityNavigationContextService {

	Map<String, Object> buildContextFor(String type, Long id);

}
