package io.kestra.jdbc.runner;

import io.kestra.core.models.Pauseable;
import io.kestra.jdbc.JdbcTableConfigs;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Singleton
@Slf4j
public class GenericJdbcQueue implements Pauseable, Closeable {

    private final JooqDSLContextWrapper dslContextWrapper;

    private final Table<Record> table;

    private final AtomicBoolean paused = new AtomicBoolean(false);

    private final AtomicBoolean closed = new AtomicBoolean(false);

    @Override
    public void pause() {
        this.paused.set(true);
    }

    @Override
    public void resume() {
        this.paused.set(false);
    }

    @Override
    public void close() throws IOException {
        this.closed.set(true);
    }

    protected interface Fields {
        String OFFSET = "offset";
        String TOPIC = "topic";
        String NAMESPACE = "namespace";
        String TENANT = "tenant";
        String VALUE = "value";
    }

    public GenericJdbcQueue(ApplicationContext applicationContext) {
        this.dslContextWrapper = applicationContext.getBean(JooqDSLContextWrapper.class);
        JdbcTableConfigs tableConfigs = applicationContext.getBean(JdbcTableConfigs.class);
        this.table = DSL.table(tableConfigs.tableConfig("generic_queues").table());
    }

    protected AtomicReference<Result<Record>> poll() {
        final AtomicReference<Result<Record>> records = new AtomicReference<>();
        if (this.paused.get() || this.closed.get()) {
            log.error("Cannot poll a paused / closed queue");
            return records;
        }
        dslContextWrapper.transaction(configuration -> {
            DSLContext context = DSL.using(configuration);
            records.set(context
                .select()
                .from(table)
                .orderBy(DSL.field(DSL.quotedName(Fields.OFFSET)).asc())
                .fetch());
        });
        return records;
    }

    public void receive(String namespace, String tenant, String topic, Consumer<String> consumer) {
        Result<Record> records = this.poll().get();
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Object> processed = records.map(record -> record.get(DSL.field(Fields.VALUE)));
        processed.forEach(obj -> {
            consumer.accept((String) obj);
        });
    }

    public void emit(String namespace, String tenant, String topic, byte[] value) {
        if (this.closed.get()) {
            return;
        }
        dslContextWrapper.transaction(configuration -> {
            DSLContext context = DSL.using(configuration);
            context.insertInto(table)
                .columns(DSL.field(DSL.quotedName(Fields.VALUE)),
                    DSL.field(DSL.quotedName(Fields.TOPIC)),
                    DSL.field(DSL.quotedName(Fields.NAMESPACE)),
                    DSL.field(DSL.quotedName(Fields.TENANT)))
                .values(JSONB.valueOf(new String(value)), topic, namespace, tenant)
                .execute();
        });
    }
}
