package io.kestra.controller;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.micronaut.context.annotation.Requires;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Restricts a bean to the server types that can host a {@link io.kestra.core.worker.Controller}.
 * Convenient annotation for
 * {@code @Requires(property = "kestra.server-type", pattern = "(CONTROLLER|STANDALONE|WEBSERVER)")}.
 * <p>
 * A {@code WEBSERVER} and a {@code STANDALONE} server both start an embedded controller unless
 * {@code --no-controller} is passed, so any bean the controller needs must also be available there.
 * Such beans stay uninstantiated when the embedded controller is disabled because nothing requests
 * the {@code Controller} bean in that case.
 * <p>
 * Every gRPC {@link io.kestra.controller.grpc.WorkerControllerService} must carry this annotation
 * rather than a narrower requirement: a service missing from the context is silently absent from the
 * gRPC server, and workers only discover it as an {@code UNIMPLEMENTED} error at task runtime.
 */
@Documented
@Requires(property = "kestra.server-type", pattern = "(CONTROLLER|STANDALONE|WEBSERVER)")
@Retention(RUNTIME)
@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD })
public @interface RequiresControllerServer {
}
