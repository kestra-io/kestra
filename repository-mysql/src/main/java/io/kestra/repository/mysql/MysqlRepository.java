package io.kestra.repository.mysql;

import io.kestra.core.models.DeletedInterface;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.jdbc.AbstractJdbcRepository;
import io.micronaut.context.ApplicationContext;
import org.jooq.Record;
import org.jooq.RecordMapper;
import org.jooq.Select;

import java.util.List;

public class MysqlRepository<T extends DeletedInterface>  extends AbstractJdbcRepository<T> {
    public MysqlRepository(Class<T> cls, ApplicationContext applicationContext) {
        super(cls, applicationContext);
    }

    public <R extends Record, E> ArrayListTotal<E> fetchPage(Select<R> select, RecordMapper<? super R, E> mapper) {
        List<E> map = select
            .fetch()
            .map(mapper);

        return new ArrayListTotal<>(map, dslContext.fetchOne("SELECT FOUND_ROWS()").into(Integer.class));
    }
}
