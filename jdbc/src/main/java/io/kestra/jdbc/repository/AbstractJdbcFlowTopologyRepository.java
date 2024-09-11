package io.kestra.jdbc.repository;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.repositories.FlowTopologyRepositoryInterface;
import io.kestra.jdbc.runner.JdbcIndexerInterface;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AbstractJdbcFlowTopologyRepository extends AbstractJdbcRepository implements FlowTopologyRepositoryInterface, JdbcIndexerInterface<FlowTopology> {
    protected final io.kestra.jdbc.AbstractJdbcRepository<FlowTopology> jdbcRepository;

    public AbstractJdbcFlowTopologyRepository(io.kestra.jdbc.AbstractJdbcRepository<FlowTopology> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public List<FlowTopology> findByFlow(String tenantId, String namespace, String flowId, Boolean destinationOnly) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                List<Condition> ors = new ArrayList<>();
                ors.add(
                    DSL.and(
                        buildTenantCondition("destination", tenantId),
                        field("destination_namespace").eq(namespace),
                        field("destination_id").eq(flowId)
                    )
                );

                if (!destinationOnly) {
                    ors.add(
                        DSL.and(
                            buildTenantCondition("source", tenantId),
                            field("source_namespace").eq(namespace),
                            field("source_id").eq(flowId)
                        )
                    );
                }

                Select<Record1<Object>> from = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(DSL.or(ors));

                return this.jdbcRepository.fetch(from);
            });
    }

    @Override
    public List<FlowTopology> findByNamespace(String tenantId, String namespace) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                List<Condition> ors = new ArrayList<>();
                ors.add(
                    DSL.and(
                        buildTenantCondition("destination", tenantId),
                        field("destination_namespace").eq(namespace),
                        buildTenantCondition("source", tenantId),
                        field("source_namespace").eq(namespace)
                    )
                );

                Select<Record1<Object>> from = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(DSL.or(ors));

                return this.jdbcRepository.fetch(from);
            });
    }

    public void save(Flow flow, List<FlowTopology> flowTopologies) {
        jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                DSLContext context = DSL.using(configuration);

                context
                    .delete(this.jdbcRepository.getTable())
                    .where(DSL.or(
                        DSL.and(
                            buildTenantCondition("destination", flow.getTenantId()),
                            field("destination_namespace").eq(flow.getNamespace()),
                            field("destination_id").eq(flow.getId())
                        ),
                        DSL.and(
                            buildTenantCondition("source", flow.getTenantId()),
                            field("source_namespace").eq(flow.getNamespace()),
                            field("source_id").eq(flow.getId())
                        )
                    ))
                    .execute();

                if (!flowTopologies.isEmpty()) {
                    context
                        .batch(flowTopologies
                            .stream()
                            .map(flowTopology -> buildMergeStatement(context, flowTopology))
                            .toList()
                        )
                        .execute();
                }
            });
    }

    protected DMLQuery<Record> buildMergeStatement(DSLContext context, FlowTopology flowTopology) {
        return context.mergeInto(this.jdbcRepository.getTable())
            .using(context.selectOne())
            .on(AbstractJdbcRepository.field("key").eq(this.jdbcRepository.key(flowTopology)))
            .whenMatchedThenUpdate()
            .set(this.jdbcRepository.persistFields(flowTopology))
            .whenNotMatchedThenInsert()
            .set(AbstractJdbcRepository.field("key"), this.jdbcRepository.key(flowTopology))
            .set(this.jdbcRepository.persistFields(flowTopology));
    }

    @Override
    public FlowTopology save(FlowTopology flowTopology) {
        this.jdbcRepository.persist(flowTopology);

        return flowTopology;
    }

    @Override
    public FlowTopology save(DSLContext dslContext, FlowTopology flowTopology) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(flowTopology);
        this.jdbcRepository.persist(flowTopology, dslContext, fields);

        return flowTopology;
    }

    protected Condition buildTenantCondition(String prefix, String tenantId) {
        return tenantId == null ? field(prefix + "_tenant_id").isNull() : field(prefix + "_tenant_id").eq(tenantId);
    }
}
