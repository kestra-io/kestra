package io.kestra.jdbc;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import org.jooq.BindingGetResultSetContext;
import org.jooq.BindingSetStatementContext;
import org.jooq.Converter;
import org.jooq.impl.AbstractBinding;

/**
 * Read-only binding returning a JSON column as UTF-8 text bytes, so that on PostgreSQL and MySQL the JSON parser
 * skips the {@code String} the driver would decode first: see ExecutionReadBenchmark in jmh-benchmarks.
 */
public final class JsonBytesBinding extends AbstractBinding<Object, byte[]> {
    private static final Converter<Object, byte[]> CONVERTER = Converter.fromNullable(
        Object.class,
        byte[].class,
        value -> value.toString().getBytes(StandardCharsets.UTF_8)
    );

    @Override
    public Converter<Object, byte[]> converter() {
        return CONVERTER;
    }

    @Override
    public void get(BindingGetResultSetContext<byte[]> ctx) throws SQLException {
        ctx.value(ctx.resultSet().getBytes(ctx.index()));
    }

    @Override
    public void set(BindingSetStatementContext<byte[]> ctx) throws SQLException {
        throw new SQLFeatureNotSupportedException("JsonBytesBinding only reads, write the column through its JSON field.");
    }
}
