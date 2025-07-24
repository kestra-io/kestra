package io.kestra.core.test;

import io.kestra.core.models.DeletedInterface;
import io.kestra.core.models.HasUID;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.test.flow.UnitTestResult;

import java.time.Instant;
import java.util.List;


public record TestSuiteRunEntity(
    String uid,
    String id,
    String tenantId,
    boolean deleted,
    Instant startDate,
    Instant endDate,

    String testSuiteId,
    String testSuiteUid,

    String namespace,
    String flowId,
    TestState state,
    List<UnitTestResult> results
) implements DeletedInterface, TenantInterface, HasUID {

    public static TestSuiteRunEntity create(String tenantId, TestSuiteUid testSuiteUid, TestSuiteRunResult testSuiteRunResult) {
        return new TestSuiteRunEntity(
            testSuiteRunResult.id(),
            testSuiteRunResult.id(),
            tenantId,
            false,
            testSuiteRunResult.startDate(),
            testSuiteRunResult.endDate(),
            testSuiteRunResult.testSuiteId(),
            testSuiteUid.toString(),
            testSuiteRunResult.namespace(),
            testSuiteRunResult.flowId(),
            testSuiteRunResult.state(),
            testSuiteRunResult.results()
        );
    }

    public TestSuiteRunEntity delete() {
        return new TestSuiteRunEntity(
            this.uid,
            this.id,
            this.tenantId,
            true,
            this.startDate,
            this.endDate,
            this.testSuiteId,
            this.testSuiteUid,
            this.namespace,
            this.flowId,
            this.state,
            this.results
        );
    }

    /**
     * only used for backup
     * @param newTenantId the tenant to migrate to
     */
    public TestSuiteRunEntity migrateToTenant(String newTenantId) {
        return new TestSuiteRunEntity(
            this.uid,
            this.id,
            newTenantId,
            this.deleted,
            this.startDate,
            this.endDate,
            this.testSuiteId,
            this.testSuiteUid,
            this.namespace,
            this.flowId,
            this.state,
            this.results
        );
    }

    @Override
    public boolean isDeleted() {
        return this.deleted;
    }

    @Override
    public String getTenantId() {
        return this.tenantId;
    }

    public TestSuiteRunResult toModel() {
        return new TestSuiteRunResult(
            this.id(),
            this.testSuiteId(),
            this.namespace(),
            this.flowId(),
            this.state(),
            this.startDate(),
            this.endDate(),
            this.results()
        );
    }
}
