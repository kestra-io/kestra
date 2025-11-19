package io.kestra.jdbc.repository;

import io.kestra.core.queues.QueueService;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.utils.ListUtils;
import io.micronaut.data.model.Pageable;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Base JDBC repository for CRUD operations.
 * <p>
 * NOTE: it uses the <code>defaultFilter(tenantId)</code> for querying.
 * If the child repository uses a default filter, it should override it.
 * <p>
 * For example, to avoid supporting allowDeleted:
 * <pre>{@code
 * @Override
 * protected Condition defaultFilter(String tenantId) {
 *     return buildTenantCondition(tenantId);
 * }
 *
 * @Override
 * protected Condition defaultFilter() {
 *     return DSL.trueCondition();
 * }
 * }</pre>
 *
 * @param <T> the type of the persisted entity.
 */
public abstract class AbstractJdbcCrudRepository<T> extends AbstractJdbcRepository {
    protected static final Field<String> KEY_FIELD = field("key", String.class);
    protected static final Field<String> VALUE_FIELD = field("value", String.class);

    protected io.kestra.jdbc.AbstractJdbcRepository<T> jdbcRepository;
    protected QueueService queueService;

    public AbstractJdbcCrudRepository(io.kestra.jdbc.AbstractJdbcRepository<T> jdbcRepository, QueueService queueService) {
        this.jdbcRepository = jdbcRepository;
        this.queueService = queueService;
    }

    /**
     * Creates an item: persist it inside the database and return it.
     * It uses an insert on conflict update to avoid concurrent write issues.
     */
    public T create(T item) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(item);
        this.jdbcRepository.persist(item, fields);

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
     * Update an item: persist it inside the database and return it.
     * It uses an update statement, so the item must be already present in the database.
     */
    public T update(T current) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSL.using(configuration)
                    .update(this.jdbcRepository.getTable())
                    .set(this.jdbcRepository.persistFields((current)))
                    .where(KEY_FIELD.eq(queueService.key(current)))
                    .execute();

                return current;
            });
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
            .transactionResult(configuration -> {
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
            .transactionResult(configuration -> {
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
        return Flux.create(emitter -> this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                DSLContext context = DSL.using(configuration);

                var select = context
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(defaultFilter)
                    .and(condition);

                if (orderByFields != null) {
                    select.orderBy(orderByFields);
                }

                try (Stream<Record1<String>> stream = select.fetchSize(FETCH_SIZE).stream()){
                    stream.map((Record record) -> jdbcRepository.map(record))
                        .forEach(emitter::next);
                } finally {
                    emitter.complete();
                }
            }), FluxSink.OverflowStrategy.BUFFER);
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
            .transactionResult(configuration -> {
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
            .transactionResult(configuration -> {
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
        return Flux.create(emitter -> this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                DSLContext context = DSL.using(configuration);

                var select = context
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(defaultFilter);

                try (Stream<Record1<String>> stream = select.fetchSize(FETCH_SIZE).stream()){
                    stream.map((Record record) -> jdbcRepository.map(record))
                        .forEach(emitter::next);
                } finally {
                    emitter.complete();
                }
            }), FluxSink.OverflowStrategy.BUFFER);
    }

    /**
     * Find all items, for all tenants.
     * WARNING: this method should never be used inside the API as it didn't enforce tenant selection!
     */
    public List<T> findAllForAllTenants() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                var select = DSL
                    .using(configuration)
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter());

                return this.jdbcRepository.fetch(select);
            });
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
}
