package io.kestra.core.junit.annotations;

import io.kestra.core.junit.extensions.TriggerEvaluationExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(TriggerEvaluationExtension.class)
public @interface EvaluateTrigger {
    String flow();

    String triggerId();
}
