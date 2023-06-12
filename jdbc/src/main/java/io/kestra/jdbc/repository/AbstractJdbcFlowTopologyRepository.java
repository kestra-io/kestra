package io.kestra.jdbc.repository;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.repositories.FlowTopologyRepositoryInterface;
import io.kestra.jdbc.runner.JdbcIndexerInterface;
import jakarta.inject.Singleton;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public abstract class AbstractJdbcFlowTopologyRepository extends AbstractJdbcRepository implements FlowTopologyRepositoryInterface, JdbcIndexerInterface<FlowTopology> {
    protected final io.kestra.jdbc.AbstractJdbcRepository<FlowTopology> jdbcRepository;

    public AbstractJdbcFlowTopologyRepository(io.kestra.jdbc.AbstractJdbcRepository<FlowTopology> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public List<FlowTopology> findByFlow(String namespace, String flowId, Boolean destinationOnly) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                List<Condition> ors = new ArrayList<>();
                ors.add(
                    DSL.and(
                        field("destination_namespace").eq(namespace),
                        field("destination_id").eq(flowId)
                    )
                );

                if (!destinationOnly) {
                    ors.add(
                        DSL.and(
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
    public List<FlowTopology> findByNamespace(String namespace) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                List<Condition> ors = new ArrayList<>();
                ors.add(
                    DSL.and(
                        field("destination_namespace").eq(namespace),
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
                            field("destination_namespace").eq(flow.getNamespace()),
                            field("destination_id").eq(flow.getId())
                        ),
                        DSL.and(
                            field("source_namespace").eq(flow.getNamespace()),
                            field("source_id").eq(flow.getId())
                        )
                    ))
                    .execute();

                if (flowTopologies.size() > 0) {context
                        .batch(flowTopologies
                            .stream()
                            .map(flowTopology -> context.insertInto(this.jdbcRepository.getTable())
                                .set(AbstractJdbcRepository.field("key"), this.jdbcRepository.key(flowTopology))
                                .set(this.jdbcRepository.persistFields(flowTopology))
                            )
                            .collect(Collectors.toList())
                        )
                        .execute();
                }
            });
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
}
