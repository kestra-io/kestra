package io.kestra.core.serializers;

import java.io.Serial;
import java.util.List;

import io.kestra.core.models.TenantInterface;

import jakarta.inject.Singleton;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

/**
 * Strips {@code tenantId} from every {@link TenantInterface} serialized by the Micronaut-managed mapper,
 * so it never reaches an HTTP API response.
 * <p>
 * This has no explicit registration: Micronaut injects every {@link ValueSerializerModifier} bean into its
 * {@code ObjectMapperFactory}. It is therefore Jackson 3 based, matching Micronaut 5 — a Jackson 2
 * {@code BeanSerializerModifier} bean would simply never be collected, and {@code tenantId} would start
 * leaking into responses with nothing failing to indicate it.
 * <p>
 * Note this applies only to the Micronaut mapper; {@link JacksonMapper} deliberately keeps {@code tenantId}
 * since it serializes for internal storage and queues.
 */
@Singleton
public class TenantSerializer extends ValueSerializerModifier {
    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public List<BeanPropertyWriter> changeProperties(
        SerializationConfig config,
        BeanDescription.Supplier beanDesc,
        List<BeanPropertyWriter> beanProperties) {
        if (!TenantInterface.class.isAssignableFrom(beanDesc.getBeanClass())) {
            return beanProperties;
        }

        return beanProperties.stream()
            .filter(property -> !property.getName().equals("tenantId"))
            .toList();
    }
}
