package io.kestra.core.serializers;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import io.kestra.core.models.TenantInterface;
import io.micronaut.core.annotation.Introspected;

import java.io.Serial;
import java.util.List;
import jakarta.inject.Singleton;

@Introspected
@Singleton
public class TenantSerializer extends BeanSerializerModifier {
    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public List<BeanPropertyWriter> changeProperties(
        SerializationConfig config,
        BeanDescription beanDesc,
        List<BeanPropertyWriter> beanProperties
    ) {
        if (!TenantInterface.class.isAssignableFrom(beanDesc.getBeanClass())) {
            return beanProperties;
        }

        return beanProperties.stream()
            .filter(property -> !property.getName().equals("tenantId"))
            .toList();
    }
}
