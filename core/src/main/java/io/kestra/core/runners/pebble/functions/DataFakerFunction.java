package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import net.datafaker.Faker;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DataFakerFunction implements Function {

    private static final String LOCALE_ARG = "locale";
    public static final String EXPR_ARG = "expr";
    public static String NAME = "datafaker";

    private final Faker defaultFaker;

    public DataFakerFunction() {
        this.defaultFaker = new Faker();
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {

        Faker faker = defaultFaker;

        if (args.containsKey(LOCALE_ARG)) {
            Object localeArg = args.get(LOCALE_ARG);
            if (!(localeArg instanceof List<?> localValue) || !localValue.stream().allMatch(o -> o instanceof String)) {
                throw new PebbleException(null,
                    "Invalid type for argument '%s' in function '%s'. Expected a list of strings, but got: %s."
                        .formatted(LOCALE_ARG, NAME, localeArg),
                    lineNumber,
                    self.getName()
                );
            }
            switch (localValue.size()) {
                case 1 -> faker = new Faker(Locale.of((String)localValue.get(0)));
                case 2 -> faker = new Faker(Locale.of((String)localValue.get(0), (String)localValue.get(1)));
                case 3 -> faker = new Faker(Locale.of((String)localValue.get(0), (String)localValue.get(1), (String)localValue.get(2)));
                default -> throw new PebbleException(null,
                    "Invalid value for argument '%s' in function '%s'. Expected format: [language, country, variant], but received: %s.".formatted(LOCALE_ARG, NAME, localValue),
                    lineNumber,
                    self.getName()
                );
            }
        }

        Object exprObj = args.get(EXPR_ARG);
        if (!(exprObj instanceof String expression) || expression.isBlank()) {
            throw new PebbleException(null,
                "Missing or invalid '%s' argument in function '%s'. A non-empty string is required."
                    .formatted(EXPR_ARG, NAME),
                lineNumber,
                self.getName()
            );
        }
        return faker.expression(expression);
    }

    @Override
    public List<String> getArgumentNames() {
        return List.of(EXPR_ARG, LOCALE_ARG);
    }
}
