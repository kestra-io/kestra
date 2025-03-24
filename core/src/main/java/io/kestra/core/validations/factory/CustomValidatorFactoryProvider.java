package io.kestra.core.validations.factory;

import io.kestra.core.models.property.PropertyValueExtractor;
import io.micronaut.configuration.hibernate.validator.ValidatorFactoryProvider;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.TypeHint;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.*;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

/**
 * Produce a Validator factory provider that replace {@link ValidatorFactoryProvider} from micronaut
 * hibernate validator. This has to be done because of a conflict between micronaut validation and micronaut hibernate validation
 * that prevent {@link jakarta.validation.valueextraction.ValueExtractor} to work
 * <br>
 * This provider allows to manually register the ValueExtractors. To do that, they have to be injected
 * and set in the {@link CustomValidatorFactoryProvider#configureValueExtractor(Configuration)} method
 */
@Factory
@Requires(classes = HibernateValidator.class)
@TypeHint(HibernateValidator.class)
@Replaces(ValidatorFactoryProvider.class)
public class CustomValidatorFactoryProvider {

    @Inject
    protected Optional<MessageInterpolator> messageInterpolator = Optional.empty();

    @Inject
    protected Optional<TraversableResolver> traversableResolver = Optional.empty();

    @Inject
    protected Optional<ConstraintValidatorFactory> constraintValidatorFactory = Optional.empty();

    @Inject
    protected Optional<ParameterNameProvider> parameterNameProvider = Optional.empty();

    @Inject
    protected PropertyValueExtractor propertyValueExtractor;

    @Value("${hibernate.validator.ignore-xml-configuration:true}")
    protected boolean ignoreXmlConfiguration = true;

    /**
     * Produces a Validator factory class.
     * @param environment optional param for environment
     * @return validator factory
     */
    @Singleton
    @Requires(classes = HibernateValidator.class)
    @Replaces(ValidatorFactory.class)
    ValidatorFactory validatorFactory(Optional<Environment> environment) {
        Configuration<?> validatorConfiguration = Validation.byDefaultProvider()
            .configure();

        validatorConfiguration.messageInterpolator(messageInterpolator.orElseGet(ParameterMessageInterpolator::new));
        messageInterpolator.ifPresent(validatorConfiguration::messageInterpolator);
        traversableResolver.ifPresent(validatorConfiguration::traversableResolver);
        constraintValidatorFactory.ifPresent(validatorConfiguration::constraintValidatorFactory);
        parameterNameProvider.ifPresent(validatorConfiguration::parameterNameProvider);

        if (ignoreXmlConfiguration) {
            validatorConfiguration.ignoreXmlConfiguration();
        }
        environment.ifPresent(env -> {
            Optional<Properties> config = env.getProperty("hibernate.validator", Properties.class);
            config.ifPresent(properties -> {
                for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                    Object value = entry.getValue();
                    if (value != null) {
                        validatorConfiguration.addProperty(
                            "hibernate.validator." + entry.getKey(),
                            value.toString()
                        );
                    }
                }
            });
        });

        configureValueExtractor(validatorConfiguration);

        return validatorConfiguration.buildValidatorFactory();
    }

    /**
     * The custom ValueExtractors has to be set here
     */
    protected void configureValueExtractor(Configuration<?> validatorConfiguration ){
        validatorConfiguration.addValueExtractor(propertyValueExtractor);
    }
}
