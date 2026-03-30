package io.kestra.jdbc.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.repositories.ConcurrencyLimitRepositoryInterface;
import io.kestra.core.runners.ConcurrencyLimit;

public class AbstractJdbcConcurrencyLimitRepository extends AbstractJdbcRepository implements ConcurrencyLimitRepositoryInterface {
    protected io.kestra.jdbc.AbstractJdbcRepository<ConcurrencyLimit> jdbcRepository;

    public AbstractJdbcConcurrencyLimitRepository(io.kestra.jdbc.AbstractJdbcRepository<ConcurrencyLimit> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public List<ConcurrencyLimit> find(String tenantId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(this.buildTenantCondition(tenantId));

                return this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public ConcurrencyLimit update(ConcurrencyLimit concurrencyLimit) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(concurrencyLimit);
        this.jdbcRepository.persist(concurrencyLimit, fields);

        return concurrencyLimit;
    }

    @Override
    public Optional<ConcurrencyLimit> findById(String tenantId, String namespace, String flowId) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(this.buildTenantCondition(tenantId))
                    .and(field("namespace").eq(namespace))
                    .and(field("flow_id").eq(flowId));
                return this.jdbcRepository.fetchOne(select);
            });
    }
}
