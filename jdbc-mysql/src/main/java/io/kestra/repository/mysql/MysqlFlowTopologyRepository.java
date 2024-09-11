package io.kestra.repository.mysql;

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
@MysqlRepositoryEnabled
public class MysqlFlowTopologyRepository extends AbstractJdbcFlowTopologyRepository {
    @Inject
    public MysqlFlowTopologyRepository(@Named("flowtopologies") MysqlRepository<FlowTopology> repository) {
        super(repository);
    }

    @Override
    protected DMLQuery<Record> buildMergeStatement(DSLContext context, FlowTopology flowTopology) {
        return context.insertInto(this.jdbcRepository.getTable())
            .set(AbstractJdbcRepository.field("key"), this.jdbcRepository.key(flowTopology))
            .set(this.jdbcRepository.persistFields(flowTopology))
            .onDuplicateKeyUpdate()
            .set(this.jdbcRepository.persistFields(flowTopology));
    }
}
