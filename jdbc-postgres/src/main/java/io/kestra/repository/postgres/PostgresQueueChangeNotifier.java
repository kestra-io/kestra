package io.kestra.repository.postgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.jooq.Configuration;
import org.jooq.exception.DataAccessException;

import io.kestra.queue.jdbc.client.QueueChangeNotifier;

import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

/**
 * Emits a Postgres {@code NOTIFY} on the channel for the published queue, using the same
 * connection/transaction as the INSERT (see {@link QueueChangeNotifier}), so Postgres only
 * delivers it to listeners once the message is durably committed.
 */
@Singleton
@PostgresQueueEnabled
public class PostgresQueueChangeNotifier implements QueueChangeNotifier {
    @Override
    public void notifyChange(Configuration configuration, String queueName, @Nullable String routingKey) {
        Connection connection = configuration.connectionProvider().acquire();
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_notify(?, ?)")) {
            statement.setString(1, PgQueueChannels.channelFor(queueName));
            statement.setString(2, routingKey == null ? "" : routingKey);
            statement.execute();
        } catch (SQLException e) {
            throw new DataAccessException("Unable to notify the Postgres queue channel for [" + queueName + "]", e);
        } finally {
            configuration.connectionProvider().release(connection);
        }
    }
}
