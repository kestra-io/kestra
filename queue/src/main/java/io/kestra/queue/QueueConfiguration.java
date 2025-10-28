package io.kestra.queue;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

@ConfigurationProperties(value = "kestra.queue")
public record QueueConfiguration(
    List<BroadcastQueueDefinitionsConfiguration> broadcast,
    List<DispatchQueueDefinitionsConfiguration> dispatch,

    @NotNull
    String type
) {

    @Getter
    public abstract static class DefinitionsConfiguration<T extends GenericEvent> {
        Class<T> cls;

        @SuppressWarnings("unchecked")
        public void setCls(String cls) {
            try {
                this.cls = (Class<T>) Class.forName(cls);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("Unable to load class: " + cls, e);
            }
        }
    }

    @EachProperty(value = "definitions.broadcast", list = true)
    public static class BroadcastQueueDefinitionsConfiguration extends DefinitionsConfiguration<BroadcastEvent> {
    }

    @EachProperty(value = "definitions.dispatch", list = true)
    public static class DispatchQueueDefinitionsConfiguration extends DefinitionsConfiguration<DispatchEvent> {
    }

    @EachProperty(value = "definitions.keyed-dispatch", list = true)
    public static class KeyDispatchQueueDefinitionsConfiguration extends DefinitionsConfiguration<KeyedDispatchEvent> {
    }
}
