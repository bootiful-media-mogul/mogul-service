package com.joshlong.mogul.api.notifications.ably;

import io.ably.lib.rest.Auth;
import io.ably.lib.types.Message;
import io.ably.lib.types.MessageExtras;
import io.ably.lib.types.PresenceMessage;
import io.ably.lib.types.ProtocolMessage;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

import java.util.Set;

class AblyHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {

		var mcs = MemberCategory.values();
		for (var ablyClass : Set.of(Auth.TokenRequest.class, Auth.TokenParams.class, Auth.AuthOptions.class,
				Auth.TokenDetails.class, Auth.AuthMethod.class, Message.class, MessageExtras.class,
				PresenceMessage.class, PresenceMessage.Action.class, ProtocolMessage.Action.class))
			hints.reflection().registerType(ablyClass, mcs);

		for (var msgPackClass : Set.of("org.msgpack.core.buffer.MessageBufferU",
				"org.msgpack.core.buffer.MessageBufferBE", "org.msgpack.core.buffer.MessageBuffer"))
			hints.reflection().registerType(TypeReference.of(msgPackClass), mcs);

	}

}
