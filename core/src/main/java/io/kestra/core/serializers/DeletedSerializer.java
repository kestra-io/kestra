package io.kestra.core.serializers;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import io.micronaut.core.annotation.Introspected;
import io.kestra.core.models.DeletedInterface;

import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Singleton;
/*
@Introspected
@Singleton
public class DeletedSerializer extends BeanSerializerModifier {
    @Override
    public List<BeanPropertyWriter> changeProperties(
        SerializationConfig config,
        BeanDescription beanDesc,
        List<BeanPropertyWriter> beanProperties
    ) {
        if (!DeletedInterface.class.isAssignableFrom(beanDesc.getBeanClass())) {
            return beanProperties;
        }

        return beanProperties.stream()
            .filter(property -> !property.getName().equals("deleted"))
            .collect(Collectors.toList());
    }
}
*/