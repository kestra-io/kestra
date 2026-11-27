package io.kestra.core.scheduler.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Hashing;

/**
 * Fingerprints of trigger definitions, used to detect that a trigger state was computed from another definition.
 */
public final class TriggerDefinitions {
    private static final ObjectWriter DEFINITION_WRITER = JacksonMapper.ofJson().writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private TriggerDefinitions() {
    }

    /**
     * Hashes the unrendered definition of the given trigger, independently of the order of its map entries.
     *
     * @throws KestraRuntimeException if the trigger cannot be serialized.
     */
    public static String hash(final AbstractTrigger trigger) {
        try {
            return Hashing.hashToString(DEFINITION_WRITER.writeValueAsString(trigger));
        } catch (JsonProcessingException e) {
            throw new KestraRuntimeException("Cannot serialize the definition of trigger '%s'.".formatted(trigger.getId()), e);
        }
    }
}
