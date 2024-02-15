package io.kestra.jdbc.repository;

import io.kestra.core.repositories.ServiceInstanceRepositoryInterface;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceStateTransition;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.DeleteResultStep;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.Table;
import org.jooq.TransactionalCallable;
import org.jooq.TransactionalRunnable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.jooq.impl.DSL.using;

@Singleton
@Getter
@Slf4j
public abstract class AbstractJdbcServiceInstanceRepository extends AbstractJdbcRepository implements ServiceInstanceRepositoryInterface {

    private static final Field<Object> STATE = field("state");
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
    public List<ServiceInstance> findAllInstancesInStates(final List<Service.ServiceState> states) {
        return this.jdbcRepository.getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> query = using(configuration)
                    .select(VALUE)
                    .from(table())
                    .where(STATE.in(states.stream().map(Enum::name).toList()));
                return this.jdbcRepository.fetch(query);
            });
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
    public ServiceStateTransition.Response mayTransitionServiceTo(final ServiceInstance instance,
                                                                  final Service.ServiceState newState,
                                                                  final String reason) {
        return transactionResult(configuration -> mayTransitServiceTo(configuration, instance, newState, reason));
    }

    /**
     * Attempt to transit the status of a given service to given new status.
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
            instance.id(),
            newState,
            reason
        );
        return ServiceStateTransition.logTransitionAndGetResponse(instance, newState, result);
    }

    /**
     * Attempt to transit the status of a given service to given new status.
     * This method may not update the service if the transition is not valid.
     *
     * @param id       the service's uid.
     * @param newState the new state of the service.
     * @return an {@link Optional} of {@link ImmutablePair} holding the old (left), and new {@link ServiceInstance} or {@code null} if transition failed (right).
     * Otherwise, an {@link Optional#empty()} if the no service can be found.
     */
    private ImmutablePair<ServiceInstance, ServiceInstance> mayUpdateStatusById(final Configuration configuration,
                                                                                final String id,
                                                                                final Service.ServiceState newState,
                                                                                final String reason) {
        // Find the ServiceInstance to be updated
        Optional<ServiceInstance> optional = findById(id, configuration, true);

        // Check whether service was found.
        if (optional.isEmpty()) {
            return null;
        }

        // Check whether the status transition is valid before saving.
        ServiceInstance serviceInstance = optional.get();
        if (serviceInstance.state().isValidTransition(newState)) {
            ServiceInstance updated = serviceInstance.updateState(newState, Instant.now(), reason);
            return new ImmutablePair<>(serviceInstance, save(updated));
        }
        return new ImmutablePair<>(serviceInstance, null);
    }

    private Table<Record> table() {
        return this.jdbcRepository.getTable();
    }
}
