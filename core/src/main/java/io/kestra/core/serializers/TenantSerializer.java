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
