package io.kestra.core.services;

import io.kestra.core.models.executions.Variables;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.storages.StorageContext;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.Map;

/**
 * Service for working with {@link Variables}.
 * It allows easily creating a {@link Variables} object.
 * <p>
 * Note: Outputs in internal storage is an EE feature so on OSS it always return an InMemory variable.
 */
@Singleton
public class VariablesService {

    /**
     * Creates a {@link Variables} from a StorageContext and an Output.
     */
    public Variables of(StorageContext context, Output outputs) {
        return of(context, outputs != null ? outputs.toMap() : Collections.emptyMap());
    }

    /**
     * Creates a {@link Variables} from a StorageContext and an Output map.
     */
    public Variables of(StorageContext context, Map<String, Object> outputs) {
        return Variables.inMemory(outputs);
    }
}
