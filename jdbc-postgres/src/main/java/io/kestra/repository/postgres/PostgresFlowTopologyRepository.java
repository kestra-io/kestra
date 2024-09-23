package io.kestra.repository.postgres;

import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.jdbc.repository.AbstractJdbcFlowTopologyRepository;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.DMLQuery;
import org.jooq.DSLContext;
import org.jooq.Record;

@Singleton
@PostgresRepositoryEnabled
public class PostgresFlowTopologyRepository extends AbstractJdbcFlowTopologyRepository {
    @Inject
    public PostgresFlowTopologyRepository(@Named("flowtopologies") PostgresRepository<FlowTopology> repository) {
        super(repository);
    }

    @Override
    protected DMLQuery<Record> buildMergeStatement(DSLContext context, FlowTopology flowTopology) {
        return context.insertInto(this.jdbcRepository.getTable())
            .set(AbstractJdbcRepository.field("key"), this.jdbcRepository.key(flowTopology))
            .set(this.jdbcRepository.persistFields(flowTopology))
            .onConflict(AbstractJdbcRepository.field("key"))
            .doUpdate()
            .set(this.jdbcRepository.persistFields(flowTopology));
    }
}
