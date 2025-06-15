package io.kestra.core.validations.validator;

import io.kestra.core.validations.Url;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
@Introspected
public class UrlValidator implements ConstraintValidator<Url, String> {
    private Pattern scheme;
    private String message;

    @Override
    public void initialize(Url constraint) {
        scheme = Pattern.compile(constraint.scheme());
        message = constraint.message();
    }

    @Override
    public boolean isValid(
        @Nullable String value,
        @NonNull AnnotationValue<Url> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        try {
            final URL url = URI.create(value).toURL();

            if (!isSchemeAllowed(url)) {
                return setViolation(
                    context,
                    "URL scheme doesn't match '" + scheme.pattern() + "' [{validatedValue}] - " + message
                );
            }
        } catch (IllegalArgumentException | MalformedURLException e) {
            return setViolation(context, message);
        }

        return true;
    }

    private boolean setViolation(ConstraintValidatorContext context, String protocol) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
            protocol
        ).addConstraintViolation();
        return false;
    }

    private boolean isSchemeAllowed(URL url) {
        final Matcher matcher = scheme.matcher(url.getProtocol());
        return matcher.matches();
    }
}
