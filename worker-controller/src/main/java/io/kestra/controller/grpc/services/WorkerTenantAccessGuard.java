package io.kestra.controller.grpc.services;

import java.util.List;
import java.util.function.Function;

import io.kestra.controller.grpc.RequestOrResponseHeader;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import jakarta.inject.Singleton;

/**
 * Checks the tenant of the data a worker writes through a gRPC call whose payload is only known once
 * decoded, and which therefore cannot be checked by a server interceptor. Every method is called on
 * the gRPC call's thread, after the payload has been deserialized. The default (OSS) implementation
 * applies no restriction; EE restricts a worker to the tenants it is allowed to serve.
 */
public interface WorkerTenantAccessGuard {

    /**
     * @param tenantOf returns the tenant a record is written to
     * @return the records the calling worker may write, in their original order
     */
    <T> List<T> allowedRecords(RequestOrResponseHeader header, List<T> records, Function<T, String> tenantOf);

    /**
     * Same as {@link #allowedRecords}, except that the result of a job still held by the calling worker is
     * always allowed, since a job dispatched before the worker lost its tenant must still complete.
     *
     * @param jobKeyOf returns the key of the job's {@link io.kestra.core.runners.WorkerJobRunning} entry
     */
    <T> List<T> allowedJobResults(RequestOrResponseHeader header, List<T> records, Function<T, String> tenantOf, Function<T, String> jobKeyOf);

    /**
     * Checks a save sent by a worker that predates the {@code tenant_id} field of save requests.
     *
     * @param tenantId the tenant of the decoded entry
     * @throws io.grpc.StatusRuntimeException with {@code PERMISSION_DENIED} when the worker may not write to this tenant
     */
    void checkUndeclaredTenant(RequestOrResponseHeader header, String tenantId);

    @Singleton
    @Requires(missingBeans = WorkerTenantAccessGuard.class)
    @Secondary
    class Default implements WorkerTenantAccessGuard {

        @Override
        public <T> List<T> allowedRecords(RequestOrResponseHeader header, List<T> records, Function<T, String> tenantOf) {
            return records;
        }

        @Override
        public <T> List<T> allowedJobResults(RequestOrResponseHeader header, List<T> records, Function<T, String> tenantOf, Function<T, String> jobKeyOf) {
            return records;
        }

        @Override
        public void checkUndeclaredTenant(RequestOrResponseHeader header, String tenantId) {
        }
    }
}
