package io.kestra.jdbc.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jooq.*;
import org.jooq.Record;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.jooq.impl.DSL;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.exceptions.AlreadyExistsException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.SoftDeletable;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.utils.ListUtils;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 * Base JDBC repository for CRUD operations.
 * <p>
 * NOTE: it uses the <code>defaultFilter(tenantId)</code> for querying.
 * If the child repository uses a default filter, it should override it.
 * <p>
 * For example, to avoid supporting allowDeleted:
 *
 * <pre>
 * {@code
 * &#64;Override
 * protected Condition defaultFilter(String tenantId) {
 *     return buildTenantCondition(tenantId);
 * }
 *
 * @Override
 * protected Condition defaultFilter() {
 *     return DSL.noCondition();
 * }
 * }
 * </pre>
 *
 * @param <T> the type of the persisted entity.
 */
public abstract class AbstractJdbcCrudRepository<T> extends AbstractJdbcRepository {
    protected io.kestra.jdbc.AbstractJdbcRepository<T> jdbcRepository;
    @Nullable
    private final ApplicationEventPublisher<CrudEvent<T>> eventPublisher;

    public AbstractJdbcCrudRepository(io.kestra.jdbc.AbstractJdbcRepository<T> jdbcRepository) {
        this(jdbcRepository, null);
    }

    public AbstractJdbcCrudRepository(io.kestra.jdbc.AbstractJdbcRepository<T> jdbcRepository, @Nullable ApplicationEventPublisher<CrudEvent<T>> eventPublisher) {
        this.jdbcRepository = jdbcRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates an item: insert it inside the database and return it.
     * It uses a plain insert, so concurrent creations of the same item cannot both succeed.
     *
     * @throws AlreadyExistsException if an item with the same key already exists.
     */
    public T create(T item) {
        String key = this.jdbcRepository.key(item);
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(item);
        try {
            this.jdbcRepository.getDslContextWrapper().transaction(
                configuration -> DSL.using(configuration)
                    .insertInto(this.jdbcRepository.getTable())
                    .set(KEY_FIELD, key)
                    .set(fields)
                    .execute()
            );
        } catch (IntegrityConstraintViolationException e) {
            // MySQL reports a duplicate key and a NOT NULL violation with the same SQL state, so check the key itself.
            if (!this.keyExists(key)) {
                throw e;
            }
            throw AlreadyExistsException.of(item.getClass().getSimpleName(), key);
        }

        this.publishEvent(CrudEvent.create(item));
        return item;
    }

    /**
     * Save an item: persist it inside the database and return it.
     * It uses an insert on conflict update to avoid concurrent write issues.
     */
    public T save(T item) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(item);
        this.jdbcRepository.persist(item, fields);

        return item;
    }

    /**
     * Creates an item: persist it inside the database and return it.
     * It uses an insert on conflict update to avoid concurrent write issues.
     */
    public T save(DSLContext context, T item) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(item);
        this.jdbcRepository.persist(item, context, fields);

        return item;
    }

    /**
     * Save a list of items: persist them inside the database and return the updated count.
     */
    public int saveBatch(List<T> items) {
        if (ListUtils.isEmpty(items)) {
            return 0;
        }

        return this.jdbcRepository.persistBatch(items);
    }

    /**
     * Update an item: persist it like {@link #save(Object)} then publish an {@link CrudEventType#UPDATE} {@link CrudEvent}.
     */
    public T update(T item) {
        this.jdbcRepository.persist(item, this.jdbcRepository.persistFields(item));
        this.publishEvent(new CrudEvent<>(item, null, CrudEventType.UPDATE));

        return item;
    }

    @SuppressWarnings("unchecked")
    public void deleteWithoutAcl(T item) {
        T deleted = (T) ((SoftDeletable<?>) item).toDeleted();
        this.jdbcRepository.persist(deleted);
    }

    /**
     * Delete an item.
     * If the entity implements {@link SoftDeletable}, it will be soft-deleted
     * Otherwise, it will be hard-deleted
     *
     * @param item the item to delete.
     * @return the deleted item.
     */
    @SuppressWarnings("unchecked")
    public T delete(T item) {
        if (item instanceof SoftDeletable<?> softDeletable) {
            T deleted = (T) softDeletable.toDeleted();
            this.jdbcRepository.persist(deleted);
            this.publishEvent(CrudEvent.delete(item));
            return deleted;
        }

        this.jdbcRepository.delete(item);
        this.publishEvent(CrudEvent.delete(item));
        return item;
    }

    /**
     * Hard-delete an item from the database.
     * Use this for physical purge operations on a single item.
     *
     * @param item the item to purge.
     * @return {@code true} if the item was deleted, {@code false} if it was not found.
     */
    public boolean purge(T item) {
        return this.jdbcRepository.delete(item) > 0;
    }

    /**
     * Hard-delete all items matching the given condition for the given tenant.
     *
     * @param tenantId the tenant ID used to build the default filter.
     * @param condition the condition to match items for deletion.
     * @return the number of deleted rows.
     */
    protected Integer purge(String tenantId, Condition condition) {
        return purge(defaultFilter(tenantId), condition);
    }

    /**
     * Hard-delete all items matching the given conditions.
     *
     * @param defaultFilter the base filter condition (e.g. tenant isolation).
     * @param condition the condition to match items for deletion.
     * @return the number of deleted rows.
     */
    protected Integer purge(Condition defaultFilter, Condition condition) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(
                configuration -> DSL.using(configuration)
                    .delete(this.jdbcRepository.getTable())
                    .where(defaultFilter)
                    .and(condition)
                    .execute()
            );
    }

    /**
     * Find one item that matches the condition.
     * <p>
     * It uses LIMIT 1 and doesn't throw if the query returns more than one result.
     *
     * @see #findOne(String, Condition, boolean, OrderField...)
     * @see #findOne(Condition, Condition, OrderField...)
     */
    @SafeVarargs
    protected final <F> Optional<T> findOne(String tenantId, Condition condition, OrderField<F>... orderByFields) {
        return findOne(defaultFilter(tenantId), condition, orderByFields);
    }

    /**
     * Find one item that matches the condition.
     * You can use <code>allowDeleted</code> to decide whether deleted items should be included or not.
     * <p>
     * It uses LIMIT 1 and doesn't throw if the query returns more than one result.
     *
     * @see #findOne(String, Condition, OrderField...)
     * @see #findOne(Condition, Condition, OrderField[])
     */
    @SafeVarargs
    protected final <F> Optional<T> findOne(String tenantId, Condition condition, boolean allowDeleted, OrderField<F>... orderByFields) {
        return findOne(defaultFilter(tenantId, allowDeleted), condition, orderByFields);
    }

    /**
     * Find one item that matches the condition.
     * <p>
     * It uses LIMIT 1 and doesn't throw if the query returns more than one result.
     *
     * @see #findOne(String, Condition, OrderField...)
     * @see #findOne(String, Condition, boolean, OrderField...)
     */
    @SafeVarargs
    protected final <F> Optional<T> findOne(Condition defaultFilter, Condition condition, OrderField<F>... orderByFields) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(defaultFilter)
                    .and(condition);

                if (orderByFields != null) {
                    select.orderBy(orderByFields);
                }

                select.limit(1);

                return this.jdbcRepository.fetchOne(select);
            });
    }

    /**
     * List all items that match the condition.
     *
     * @see #findAsync(String, Condition, OrderField...)
     * @see #findPage(Pageable, String, Condition, OrderField...)
     */
    @SafeVarargs
    protected final <F> List<T> find(String tenantId, Condition condition, OrderField<F>... orderByFields) {
        return find(defaultFilter(tenantId), condition, orderByFields);
    }

    /**
     * List all items that match the condition.
     * You can use <code>allowDeleted</code> to decide whether deleted items should be included or not.
     *
     * @see #findAsync(String, Condition, boolean, OrderField...)
     * @see #findPage(Pageable, String, Condition, boolean, OrderField...)
     */
    @SafeVarargs
    protected final <F> List<T> find(String tenantId, Condition condition, boolean allowDeleted, OrderField<F>... orderByFields) {
        return find(defaultFilter(tenantId, allowDeleted), condition, orderByFields);
    }

    /**
     * List all items that match the condition.
     *
     * @see #findAsync(Condition, Condition, OrderField...)
     * @see #findPage(Pageable, Condition, Condition, OrderField...)
     */
    @SafeVarargs
    protected final <F> List<T> find(Condition defaultFilter, Condition condition, OrderField<F>... orderByFields) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(defaultFilter)
                    .and(condition);

                if (orderByFields != null) {
                    select.orderBy(orderByFields);
                }

                return this.jdbcRepository.fetch(select);
            });
    }

    /**
     * Find all items that match the condition and return a reactive stream.
     * To avoid any potential issues with databases that load all the resultset in memory, it batches the results by <code>FETCH_SIZE</code>.
     *
     * @see #find(String, Condition, OrderField...)
     * @see #findPage(Pageable, String, Condition, OrderField...)
     */
    @SafeVarargs
    protected final <F> Flux<T> findAsync(String tenantId, Condition condition, OrderField<F>... orderByFields) {
        return findAsync(defaultFilter(tenantId), condition, orderByFields);
    }

    /**
     * Find all items that match the condition and return a reactive stream.
     * To avoid any potential issues with databases that load all the resultset in memory, it batches the results by <code>FETCH_SIZE</code>.
     * You can use <code>allowDeleted</code> to decide whether deleted items should be included or not.
     *
     * @see #find(String, Condition, boolean, OrderField...)
     * @see #findPage(Pageable, String, Condition, boolean, OrderField...)
     */
    @SafeVarargs
    protected final <F> Flux<T> findAsync(String tenantId, Condition condition, boolean allowDeleted, OrderField<F>... orderByFields) {
        return findAsync(defaultFilter(tenantId, allowDeleted), condition, orderByFields);
    }

    /**
     * Find all items that match the condition and return a reactive stream.
     * To avoid any potential issues with databases that load all the resultset in memory, it batches the results by <code>FETCH_SIZE</code>.
     *
     * @see #find(Condition, Condition, OrderField...)
     * @see #findPage(Pageable, Condition, Condition, OrderField...)
     */
    @SafeVarargs
    protected final <F> Flux<T> findAsync(Condition defaultFilter, Condition condition, OrderField<F>... orderByFields) {
        return Flux.create(
            emitter -> this.jdbcRepository
                .getDslContextWrapper()
                .transaction(configuration ->
                {
                    DSLContext context = DSL.using(configuration);

                    var select = context
                        .select(VALUE_FIELD)
                        .from(this.jdbcRepository.getTable())
                        .where(defaultFilter)
                        .and(condition);

                    if (orderByFields != null) {
                        select.orderBy(orderByFields);
                    }

                    try (var stream = select.fetchSize(FETCH_SIZE).stream()) {
                        stream.map((Record record) -> jdbcRepository.map(record))
                            .forEach(emitter::next);
                    } finally {
                        emitter.complete();
                    }
                }),
            FluxSink.OverflowStrategy.BUFFER
        );
    }

    /**
     * Find a page of items that match the condition and return them.
     *
     * @see #find(String, Condition, OrderField...)
     * @see #findAsync(String, Condition, OrderField...)
     */
    @SafeVarargs
    protected final <F> ArrayListTotal<T> findPage(Pageable pageable, String tenantId, Condition condition, OrderField<F>... orderByFields) {
        return findPage(pageable, defaultFilter(tenantId), condition, orderByFields);
    }

    /**
     * Find a page of items that match the condition and return them.
     * You can use <code>allowDeleted</code> to decide whether deleted items should be included or not.
     *
     * @see #find(String, Condition, boolean, OrderField...)
     * @see #findAsync(String, Condition, boolean, OrderField...)
     */
    @SafeVarargs
    protected final <F> ArrayListTotal<T> findPage(Pageable pageable, String tenantId, Condition condition, boolean allowDeleted, OrderField<F>... orderByFields) {
        return findPage(pageable, defaultFilter(tenantId, allowDeleted), condition, orderByFields);
    }

    /**
     * Find a page of items that match the condition and return them.
     *
     * @see #find(Condition, Condition, OrderField...)
     * @see #findAsync(Condition, Condition, OrderField...)
     */
    @SafeVarargs
    protected final <F> ArrayListTotal<T> findPage(Pageable pageable, Condition defaultFilter, Condition condition, OrderField<F>... orderByFields) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                DSLContext context = DSL.using(configuration);

                var select = context
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(defaultFilter)
                    .and(condition);

                if (orderByFields != null) {
                    select.orderBy(orderByFields);
                }

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    /**
     * Find all items.
     *
     * @see #findAllAsync(String)
     */
    public List<T> findAll(String tenantId) {
        return findAll(defaultFilter(tenantId));
    }

    /**
     * Find all items.
     *
     * @see #findAllAsync(Condition)
     */
    protected List<T> findAll(Condition defaultFilter) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(defaultFilter);

                return this.jdbcRepository.fetch(select);
            });
    }

    /**
     * Find all items and return a reactive stream.
     * To avoid any potential issues with databases that load all the resultset in memory, it batches the results by <code>FETCH_SIZE</code>.
     *
     * @see #findAll(String)
     */
    public Flux<T> findAllAsync(String tenantId) {
        return findAllAsync(defaultFilter(tenantId));
    }

    /**
     * Find all items and return a reactive stream.
     * To avoid any potential issues with databases that load all the resultset in memory, it batches the results by <code>FETCH_SIZE</code>.
     *
     * @see #findAll(Condition)
     */
    protected Flux<T> findAllAsync(Condition defaultFilter) {
        return Flux.create(
            emitter -> this.jdbcRepository
                .getDslContextWrapper()
                .transaction(configuration ->
                {
                    DSLContext context = DSL.using(configuration);

                    var select = context
                        .select(VALUE_FIELD)
                        .from(this.jdbcRepository.getTable())
                        .where(defaultFilter);

                    try (var stream = select.fetchSize(FETCH_SIZE).stream()) {
                        stream.map((Record record) -> jdbcRepository.map(record))
                            .forEach(emitter::next);
                    } finally {
                        emitter.complete();
                    }
                }),
            FluxSink.OverflowStrategy.BUFFER
        );
    }

    /**
     * Find all items, for all tenants.
     * WARNING: this method should never be used inside the API as it didn't enforce tenant selection!
     */
    public List<T> findAllForAllTenants() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter());

                return this.jdbcRepository.fetch(select);
            });
    }

    /**
     * Returns distinct values of {@code field}, optionally narrowed by {@code filters}.
     * <p>
     * Filters are routed through {@link #filter(List, String, QueryFilter.Resource)} which validates
     * them against {@code resource} and combines each filter's condition via
     * {@link #getConditionOnField}. Per-repository {@link #getColumnName} overrides (e.g.
     * FLOW_ID → {@code id} on flows, → {@code detail_flow_id} on audit logs) are therefore
     * honored. Pass {@code null} or an empty filter list to fetch the full distinct set under
     * the tenant default filter.
     * <p>
     * Concrete repositories should expose this via a public override that supplies their resource.
     */
    protected List<String> findDistinctFieldValues(
        String tenantId,
        QueryFilter.Field field,
        List<QueryFilter> filters,
        Pageable pageable,
        QueryFilter.Resource resource) {
        Field<String> column = getColumn(field).coerce(String.class);
        Condition where = defaultFilter(tenantId).and(filter(filters, null, resource));

        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(
                configuration -> DSL
                    .using(configuration)
                    .selectDistinct(column)
                    .from(this.jdbcRepository.getTable())
                    .where(where)
                    .orderBy(column.asc())
                    .limit(pageable.getSize())
                    .fetch(column)
            );
    }

    /**
     * Check whether an item matches the condition.
     */
    protected boolean exists(String tenantId, Condition condition) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(
                configuration -> DSL.using(configuration)
                    .fetchExists(this.jdbcRepository.getTable(), this.defaultFilter(tenantId).and(condition))
            );
    }

    /**
     * Count items that match the condition.
     *
     * @see #countAll(String)
     * @see #countAllForAllTenants()
     */
    protected long count(String tenantId, Condition condition) {
        return this.jdbcRepository.count(this.defaultFilter(tenantId).and(condition));
    }

    /**
     * Count all items.
     *
     * @see #count(String, Condition)
     * @see #countAllForAllTenants()
     */
    public long countAll(String tenantId) {
        return this.jdbcRepository.count(this.defaultFilter(tenantId));
    }

    /**
     * Count all items for all tenants.
     * WARNING: this method should never be used inside the API as it didn't enforce tenant selection!
     *
     * @see #count(String, Condition)
     * @see #countAll(String)
     */
    public long countAllForAllTenants() {
        return this.jdbcRepository.count(this.defaultFilter());
    }

    private boolean keyExists(String key) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(
                configuration -> DSL.using(configuration)
                    .fetchExists(this.jdbcRepository.getTable(), KEY_FIELD.eq(key))
            );
    }

    private void publishEvent(CrudEvent<T> event) {
        if (this.eventPublisher != null) {
            this.eventPublisher.publishEvent(event);
        }
    }
}
