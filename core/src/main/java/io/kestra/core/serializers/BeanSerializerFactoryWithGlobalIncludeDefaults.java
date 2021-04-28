package io.kestra.core.serializers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.cfg.PackageVersion;
import com.fasterxml.jackson.databind.cfg.SerializerFactoryConfig;
import com.fasterxml.jackson.databind.ser.BeanSerializerFactory;
import com.fasterxml.jackson.databind.ser.PropertyBuilder;
import com.fasterxml.jackson.databind.ser.SerializerFactory;

import java.lang.reflect.Field;

/**
 * Handle default values properly 
 * https://github.com/FasterXML/jackson-databind/issues/2105#issuecomment-416267742
 * https://gist.github.com/bwaldvogel/5dae899974f6ab68cab556d524460382
 */
public class BeanSerializerFactoryWithGlobalIncludeDefaults extends BeanSerializerFactory {
    private static final long serialVersionUID = 1L;
    private static final String REFLECTION_EXCEPTION_MESSAGE = "Failed to adjust " + PropertyBuilder.class + "." +
        " This workaround is probably incompatible with Jackson " + PackageVersion.VERSION;

    private final Field defaultInclusionField;
    private final Field useRealPropertyDefaultsField;

    BeanSerializerFactoryWithGlobalIncludeDefaults() {
        this(null);
    }

    private BeanSerializerFactoryWithGlobalIncludeDefaults(SerializerFactoryConfig config) {
        super(config);

        try {
            defaultInclusionField = PropertyBuilder.class.getDeclaredField("_defaultInclusion");
            defaultInclusionField.setAccessible(true);

            useRealPropertyDefaultsField = PropertyBuilder.class.getDeclaredField("_useRealPropertyDefaults");
            useRealPropertyDefaultsField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(REFLECTION_EXCEPTION_MESSAGE, e);
        }
    }

    @Override
    public SerializerFactory withConfig(SerializerFactoryConfig config) {
        if (_factoryConfig == config) {
            return this;
        }
        return new BeanSerializerFactoryWithGlobalIncludeDefaults(config);
    }

    @Override
    protected PropertyBuilder constructPropertyBuilder(SerializationConfig config, BeanDescription beanDesc) {
        PropertyBuilder propertyBuilder = super.constructPropertyBuilder(config, beanDesc);

        try {
            adjustUseRealPropertyDefaults(propertyBuilder);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(REFLECTION_EXCEPTION_MESSAGE, e);
        }

        return propertyBuilder;
    }

    private void adjustUseRealPropertyDefaults(PropertyBuilder propertyBuilder) throws ReflectiveOperationException {
        JsonInclude.Value defaultInclusion = (JsonInclude.Value) defaultInclusionField.get(propertyBuilder);

        boolean useRealPropertyDefaults = defaultInclusion.getValueInclusion() == JsonInclude.Include.NON_DEFAULT;

        useRealPropertyDefaultsField.setBoolean(propertyBuilder, useRealPropertyDefaults);
    }

}