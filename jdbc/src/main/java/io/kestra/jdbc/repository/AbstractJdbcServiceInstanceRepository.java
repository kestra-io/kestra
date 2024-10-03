package io.kestra.jdbc.repository;

import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ServiceInstanceRepositoryInterface;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceLivenessStore;
import io.kestra.core.server.ServiceLivenessUpdater;
import io.kestra.core.server.ServiceStateTransition;
import io.micronaut.data.model.Pageable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.Table;
import org.jooq.TransactionalCallable;
import org.jooq.TransactionalRunnable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.jooq.impl.DSL.using;

@Getter
@Slf4j
public abstract class AbstractJdbcServiceInstanceRepository extends AbstractJdbcRepository implements ServiceInstanceRepositoryInterface, ServiceLivenessStore, ServiceLivenessUpdater {

    private static final Field<Object> STATE = field("state");
    private static final Field<Object> TYPE = field("service_type");
    private static final Field<Object> VALUE = field("value");
    private static final Field<Instant> UPDATED_AT = field("updated_at", Instant.class);
    private static final Field<Instant> CREATED_AT = field("created_at", Instant.class);
    private static final Field<Object> SERVICE_ID = field("service_id");

    protected io.kestra.jdbc.AbstractJdbcRepository<ServiceInstance> jdbcRepository;

    public AbstractJdbcServiceInstanceRepository(final io.kestra.jdbc.AbstractJdbcRepository<ServiceInstance> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Optional<ServiceInstance> findById(final String id) {
        return jdbcRepository.getDslContextWrapper().transactionResult(
            configuration -> findById(id, configuration, false)
        );
    }

    public Optional<ServiceInstance> findById(final String id,
                                              final Configuration configuration,
                                              final boolean isForUpdate) {

        SelectConditionStep<Record1<Object>> query = using(configuration)
            .select(VALUE)
            .from(table())
            .where(SERVICE_ID.eq(id));

        return isForUpdate ?
            this.jdbcRepository.fetchOne(query.forUpdate()) :
            this.jdbcRepository.fetchOne(query);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<ServiceInstance> findAllInstancesInState(final Service.ServiceState state) {
        return this.jdbcRepository.getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> query = using(configuration)
                    .select(VALUE)
                    .from(table())
                    .where(STATE.eq(state.name()));
                return this.jdbcRepository.fetch(query);
            });
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<ServiceInstance> findAllInstancesInStates(final Set<Service.ServiceState> states) {
        return this.jdbcRepository.getDslContextWrapper()
            .transactionResult(configuration -> findAllInstancesInStates(configuration, states, false));
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<ServiceInstance> findAllInstancesBetween(final Service.ServiceType type, final Instant from, final Instant to) {
        return jdbcRepository.getDslContextWrapper().transactionResult(configuration -> {
            SelectConditionStep<Record1<Object>> query = using(configuration)
                .select(VALUE)
                .from(table())
                .where(TYPE.eq(type.name()))
                .and(CREATED_AT.lt(to))
                .and(UPDATED_AT.ge(from));

            return this.jdbcRepository.fetch(query);
        });
    }

    public List<ServiceInstance> findAllInstancesInStates(final Configuration configuration,
                                                          final Set<Service.ServiceState> states,
                                                          final boolean isForUpdate) {
        SelectConditionStep<Record1<Object>> query = using(configuration)
            .select(VALUE)
            .from(table())
            .where(STATE.in(states.stream().map(Enum::name).toList()));

        return isForUpdate ?
            this.jdbcRepository.fetch(query.forUpdate()) :
            this.jdbcRepository.fetch(query);
    }

    /**
     * Finds all service instances which are NOT {@link Service.ServiceState#RUNNING}.
     *
     * @return the list of {@link ServiceInstance}.
     */
    public List<ServiceInstance> findAllNonRunningInstances() {
        return jdbcRepository.getDslContextWrapper().transactionResult(
            configuration -> findAllNonRunningInstances(configuration, false)
        );
    }

    /**
     * Finds all service instances which are NOT {@link Service.ServiceState#RUNNING}.
     *
     * @return the list of {@link ServiceInstance}.
     */
    public List<ServiceInstance> findAllNonRunningInstances(final Configuration configuration,
                                                            final boolean isForUpdate) {
        SelectConditionStep<Record1<Object>> query = using(configuration)
            .select(VALUE)
            .from(table())
            .where(STATE.notIn(Service.ServiceState.CREATED.name(), Service.ServiceState.RUNNING.name()));

        return isForUpdate ?
            this.jdbcRepository.fetch(query.forUpdate()) :
            this.jdbcRepository.fetch(query);
    }

    /**
     * Finds all service instances which are {@link Service.ServiceState#NOT_RUNNING}.
     *
     * @return the list of {@link ServiceInstance}.
     */
    public List<ServiceInstance> findAllInstancesInNotRunningState() {
        return jdbcRepository.getDslContextWrapper().transactionResult(
            configuration -> findAllInstancesInNotRunningState(configuration, false)
        );
    }

    /**
     * Finds all service instances which are {@link Service.ServiceState#NOT_RUNNING}.
     *
     * @return the list of {@link ServiceInstance}.
     */
    public List<ServiceInstance> findAllInstancesInNotRunningState(final Configuration configuration,
                                                                   final boolean isForUpdate) {
        SelectConditionStep<Record1<Object>> query = using(configuration)
            .select(VALUE)
            .from(table())
            .where(STATE.eq(Service.ServiceState.NOT_RUNNING.name()));

        return isForUpdate ?
            this.jdbcRepository.fetch(query.forUpdate()) :
            this.jdbcRepository.fetch(query);
    }

    public void transaction(final TransactionalRunnable runnable) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(runnable);
    }

    public <T> T transactionResult(final TransactionalCallable<T> runnable) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(runnable);
    }

    public void delete(DSLContext context, ServiceInstance instance) {
        this.jdbcRepository.delete(context, instance);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public void delete(final ServiceInstance instance) {
        this.jdbcRepository.delete(instance);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public ServiceInstance save(final ServiceInstance instance) {
        this.jdbcRepository.persist(instance, this.jdbcRepository.persistFields(instance));
        return instance;
    }


    /**
     * {@inheritDoc}
     **/
    @Override
    public void update(final ServiceInstance instance) {
        this.save(instance);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<ServiceInstance> findAll() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> this.jdbcRepository.fetch(
                using(configuration).select(VALUE).from(table()))
            );
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public ArrayListTotal<ServiceInstance> find(final Pageable pageable,
                                                final Set<Service.ServiceState> states,
                                                final Set<Service.ServiceType> types) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = using(configuration);
                SelectConditionStep<Record1<Object>> select = context.select(VALUE).from(table()).where("1=1");
                if (states != null && !states.isEmpty()) {
                    select = select.and(STATE.in(states.stream().map(Enum::name).toList()));
                }
                if (types != null && !types.isEmpty()) {
                    select = select.and(TYPE.in(types.stream().map(Enum::name).toList()));
                }
                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public ServiceStateTransition.Response update(final ServiceInstance instance,
                                                  final Service.ServiceState newState,
                                                  final String reason) {
        return transactionResult(configuration -> mayTransitServiceTo(configuration, instance, newState, reason));
    }

    /**
     * Attempt to transition the state of a given service to given new state.
     * This method may not update the service if the transition is not valid.
     *
     * @param instance the service instance.
     * @param newState the new state of the service.
     * @return an optional of the {@link ServiceInstance} or {@link Optional#empty()} if the service is not running.
     */
    public ServiceStateTransition.Response mayTransitServiceTo(final Configuration configuration,
                                                               final ServiceInstance instance,
                                                               final Service.ServiceState newState,
                                                               final String reason) {
        ImmutablePair<ServiceInstance, ServiceInstance> result = mayUpdateStatusById(
            configuration,
            instance,
            newState,
            reason
        );
        return ServiceStateTransition.logTransitionAndGetResponse(instance, newState, result);
    }

    /**
     * Attempt to transition the state of a given service to given new state.
     * This method may not update the service if the transition is not valid.
     *
     * @param instance the new service instance.
     * @param newState the new state of the service.
     * @return an {@link Optional} of {@link ImmutablePair} holding the old (left), and new {@link ServiceInstance} or {@code null} if transition failed (right).
     * Otherwise, an {@link Optional#empty()} if the no service can be found.
     */
    private ImmutablePair<ServiceInstance, ServiceInstance> mayUpdateStatusById(final Configuration configuration,
                                                                                final ServiceInstance instance,
                                                                                final Service.ServiceState newState,
                                                                                final String reason) {
        // Find the ServiceInstance to be updated
        Optional<ServiceInstance> optional = findById(instance.uid(), configuration, true);

        // Check whether service was found.
        if (optional.isEmpty()) {
            return null;
        }

        // Check whether the status transition is valid before saving.
        final ServiceInstance before = optional.get();
        if (before.state().isValidTransition(newState)) {
            ServiceInstance updated = before
                .state(newState, Instant.now(), reason)
                .server(instance.server())
                .metrics(instance.metrics());
            // Synchronize
            update(updated);
            return new ImmutablePair<>(before, updated);
        }
        return new ImmutablePair<>(before, null);
    }

    private Table<Record> table() {
        return this.jdbcRepository.getTable();
    }

    /** {@inheritDoc} **/
    @Override
    public Function<String, String> sortMapping() {
        Map<String, String> mapper = Map.of(
            "createdAt", CREATED_AT.getName(),
            "updatedAt", UPDATED_AT.getName(),
            "serviceId", SERVICE_ID.getName()
        );
        return mapper::get;
    }
}
