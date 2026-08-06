package io.kestra.controller;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.kestra.controller.discovery.ControllerStorageRegistrar;
import io.kestra.controller.grpc.WorkerControllerService;
import io.kestra.controller.grpc.services.GrpcExecutionLogController;
import io.kestra.controller.grpc.services.GrpcFlowMetaStoreWorkerControllerService;
import io.kestra.controller.grpc.services.GrpcKVMetadataControllerService;
import io.kestra.controller.grpc.services.GrpcNSMetadataControllerService;
import io.kestra.controller.grpc.services.WorkerCapacityMetricsPublisher;
import io.kestra.core.models.ServerType;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.PropertySource;
import io.micronaut.inject.BeanDefinition;

import static org.assertj.core.api.Assertions.assertThat;

class RequiresControllerServerTest {

    @Test
    void shouldRegisterTheSameGrpcServicesOnEveryControllerHostingServerType() {
        // Given
        Set<Class<?>> onDedicatedController = grpcServiceTypes(ServerType.CONTROLLER);

        // Then a webserver or a standalone server running an embedded controller serves the same set,
        // otherwise workers get UNIMPLEMENTED on the missing methods.
        assertThat(onDedicatedController).isNotEmpty();
        assertThat(grpcServiceTypes(ServerType.WEBSERVER)).isEqualTo(onDedicatedController);
        assertThat(grpcServiceTypes(ServerType.STANDALONE)).isEqualTo(onDedicatedController);
    }

    @Test
    void shouldNotRegisterMetadataGrpcServicesOnServerTypesWithoutController() {
        Class<?>[] metadataServices = {
            GrpcKVMetadataControllerService.class,
            GrpcNSMetadataControllerService.class,
            GrpcFlowMetaStoreWorkerControllerService.class,
            GrpcExecutionLogController.class
        };

        assertThat(grpcServiceTypes(ServerType.WORKER)).doesNotContain(metadataServices);
        assertThat(grpcServiceTypes(ServerType.EXECUTOR)).doesNotContain(metadataServices);
        assertThat(grpcServiceTypes(ServerType.SCHEDULER)).doesNotContain(metadataServices);
    }

    @Test
    void shouldEnableControllerBeansOnWebserver() {
        try (ApplicationContext context = run(ServerType.WEBSERVER)) {
            assertThat(context.containsBean(WorkerCapacityMetricsPublisher.class)).isTrue();
            assertThat(context.containsBean(ControllerStorageRegistrar.class)).isTrue();
        }
    }

    @Test
    void shouldNotEnableControllerBeansOnWorker() {
        try (ApplicationContext context = run(ServerType.WORKER)) {
            assertThat(context.containsBean(WorkerCapacityMetricsPublisher.class)).isFalse();
            assertThat(context.containsBean(ControllerStorageRegistrar.class)).isFalse();
        }
    }

    private static Set<Class<?>> grpcServiceTypes(ServerType serverType) {
        try (ApplicationContext context = run(serverType)) {
            return context.getBeanDefinitions(WorkerControllerService.class)
                .stream()
                .filter(definition -> definition.isEnabled(context))
                .map(BeanDefinition::getBeanType)
                .collect(Collectors.toSet());
        }
    }

    private static ApplicationContext run(ServerType serverType) {
        return ApplicationContext.run(
            PropertySource.of(
                "test", Map.of(
                    "kestra.server-type", serverType.name(),
                    "kestra.controller.advertise.enabled", "true"
                )
            )
        );
    }
}
