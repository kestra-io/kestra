package io.kestra.jdbc.repository;

import java.util.List;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.impl.DSL;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.utils.ListUtils;
import io.kestra.fethr.table.TableDefinition;
import io.kestra.fethr.table.TableRepositoryInterface;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;

/**
 * Registry persistence for {@link TableDefinition}.
 *
 * <p>
 * Only the schema. The rows a table holds are in a physical table this never touches; see
 * {@link io.kestra.jdbc.tables.TableDdlSupport}.
 */
public abstract class AbstractJdbcTableRepository extends AbstractJdbcCrudRepository<TableDefinition> implements TableRepositoryInterface {

    protected AbstractJdbcTableRepository(io.kestra.jdbc.AbstractJdbcRepository<TableDefinition> jdbcRepository) {
        super(jdbcRepository);
    }

    abstract protected Condition findCondition(String query);

    @Override
    protected Condition findQueryCondition(String query) {
        return findCondition(query);
    }

    @Override
    public Optional<TableDefinition> findByName(String tenantId, String namespace, String name) {
        return findOne(
            tenantId,
            field("namespace").eq(namespace).and(field("name").eq(name))
        );
    }

    @Override
    public List<TableDefinition> findByNamespace(String tenantId, String namespace) {
        return find(tenantId, field("namespace").eq(namespace));
    }

    /**
     * A page of the registry, filtered by free text and namespace.
     *
     * <p>
     * The condition is assembled here rather than through {@code filter(…, Resource)}: upstream's
     * enum has no member for tables, and borrowing a neighbouring one would make every rejected
     * filter report the wrong resource. Only the two fields the table list actually offers are
     * honoured, and anything else is ignored rather than half-applied.
     */
    @Override
    public ArrayListTotal<TableDefinition> find(Pageable pageable, String tenantId, @Nullable List<QueryFilter> filters) {
        Condition condition = DSL.noCondition();

        for (QueryFilter filter : ListUtils.emptyOnNull(filters)) {
            Object value = filter.value();
            if (value == null) {
                continue;
            }

            condition = switch (filter.field()) {
                case QUERY -> condition.and(findCondition(value.toString()));
                case NAMESPACE -> condition.and(field("namespace").eq(value.toString()));
                default -> condition;
            };
        }

        return findPage(pageable, tenantId, condition);
    }

    @Override
    public TableDefinition update(TableDefinition table, TableDefinition previous) {
        findByName(previous.getTenantId(), previous.getNamespace(), previous.getName())
            .flatMap(current -> current.validateUpdate(table))
            .ifPresent(violation ->
            {
                throw violation;
            });

        return super.update(table);
    }

    @Override
    public Optional<TableDefinition> delete(String tenantId, String namespace, String name) {
        return findByName(tenantId, namespace, name).map(this::delete);
    }
}
