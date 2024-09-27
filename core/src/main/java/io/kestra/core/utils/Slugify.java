package io.kestra.core.utils;

import org.apache.commons.lang3.StringUtils;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public class Slugify {
    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");
    private static final Pattern DASH_PATTERN = Pattern.compile("[-_]([a-z])");

    public static String of(String input) {
        String nowhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");

        slug = slug.replace("_", "-");

        while (slug.contains("--")) {
            slug = slug.replace("--", "-");
        }

        slug = StringUtils.removeStart(slug, "-");
        slug = StringUtils.removeEnd(slug, "-");

        return slug.toLowerCase(Locale.ENGLISH);
    }

    public static String toStartCase(String input) {
        return DASH_PATTERN.matcher(input).replaceAll(match -> " " + match.group(1).toUpperCase());
    }
}
