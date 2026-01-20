package com.joshlong.mogul.api.mogul;

import com.joshlong.mogul.api.EntityContext;
import com.joshlong.mogul.api.EntityContextBuilder;
import com.joshlong.mogul.api.utils.TypeUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Map;

@Component
class MogulEntityContextBuilder implements EntityContextBuilder<Mogul> {

	private final String resolvedType = TypeUtils.typeName(Mogul.class);

	@Override
	public EntityContext buildContextFor(Long mogulId, Long entityId) {
		Assert.state(mogulId != null && entityId != null, "params are null");
		Assert.state(mogulId.equals(entityId), "params are not equal");
		return new EntityContext(this.resolvedType, Map.of("mogulId", mogulId));
	}

}
