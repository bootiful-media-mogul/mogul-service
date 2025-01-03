package com.joshlong.mogul.api.compositions;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.Configuration;

@Configuration
@RegisterReflectionForBinding({ Composition.class, Composable.class, Attachment.class })
class CompositionConfiguration {

}
