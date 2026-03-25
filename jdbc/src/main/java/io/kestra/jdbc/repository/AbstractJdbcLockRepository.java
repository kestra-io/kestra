package io.kestra.jdbc.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import io.kestra.core.lock.Lock;
import io.kestra.core.repositories.LockRepositoryInterface;
import io.kestra.core.utils.IdUtils;

public class AbstractJdbcLockRepository extends AbstractJdbcRepository implements LockRepositoryInterface {
    private final io.kestra.jdbc.AbstractJdbcRepository<Lock> jdbcRepository;

    public AbstractJdbcLockRepository(io.kestra.jdbc.AbstractJdbcRepository<Lock> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public Optional<Lock> findById(String category, String id) {
        return jdbcRepository.getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(field("key").eq(IdUtils.fromParts(category, id)));
                return this.jdbcRepository.fetchOne(select);
            });
    }

    @Override
    public boolean create(Lock newLock) {
        try {
            Map<Field<Object>, Object> finalFields = this.jdbcRepository.persistFields(newLock);
            return jdbcRepository.getDslContextWrapper()
                .transactionResult(configuration ->
                {
                    var dslContext = DSL.using(configuration);
                    var insert = dslContext
                        .insertInto(this.jdbcRepository.getTable())
                        .set(field("key"), newLock.uid())
                        .set(finalFields);
                    int inserted;
                    if (dslContext.configuration().dialect().supports(SQLDialect.POSTGRES) || dslContext.configuration().dialect().supports(SQLDialect.MYSQL)) {
                        inserted = insert.onDuplicateKeyIgnore().execute();
                    } else {
                        inserted = insert.execute();
                    }
                    return inserted > 0;
                });

        } catch (DataAccessException e) {
            // if we cannot insert, this means another process creates the lock before us so we return false
            // it should only happen for H2 as Postgres and MySQL uses onDuplicateKeyIgnore
            return false;
        }
    }

    @Override
    public void deleteById(String category, String id) {
        this.jdbcRepository.getDslContextWrapper()
            .transaction(
                configuration -> DSL.using(configuration)
                    .delete(this.jdbcRepository.getTable())
                    .where(field("key").eq(IdUtils.fromParts(category, id)))
                    .execute()
            );
    }

    @Override
    public List<Lock> deleteByOwner(String owner) {
        return this.jdbcRepository.getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL.using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(field("owner").eq(owner));
                var locks = this.jdbcRepository.fetch(select.forUpdate());
                locks.forEach(lock -> this.jdbcRepository.delete(lock));
                return locks;
            }
            );
    }
}
