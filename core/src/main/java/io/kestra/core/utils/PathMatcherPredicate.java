package io.kestra.core.utils;

import jakarta.annotation.Nullable;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import static java.util.function.Predicate.not;

/**
 * Simple {@link Predicate} implementation for matching {@link Path paths}
 * based on given glob or regex expressions.
 */
public final class PathMatcherPredicate implements Predicate<Path> {

    private static final String SYNTAX_GLOB = "glob:";
    private static final String SYNTAX_REGEX = "regex:";

    /**
     * Static factory method for constructing a new {@link PathMatcherPredicate} instance.
     *
     * @param patterns a list of glob or regex expressions.
     * @return a new {@link PathMatcherPredicate}.
     */
    public static PathMatcherPredicate matches(final List<String> patterns) {
        return new PathMatcherPredicate(null, patterns);
    }

    /**
     * Static factory method for constructing a new {@link PathMatcherPredicate} instance.
     *
     * @param basePath a base path to chroot all patterns - may be {@code null}.
     * @param patterns a list of glob or regex expressions.
     * @return a new {@link PathMatcherPredicate}.
     */
    public static PathMatcherPredicate matches(final Path basePath, final List<String> patterns) {
        return new PathMatcherPredicate(basePath, patterns);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing new {@link PathMatcherPredicate}.
     */
    public static class Builder {
        private List<String> includes = List.of();
        private List<String> excludes = List.of();

        public Builder includes(final List<String> includes) {
            this.includes = Optional.ofNullable(includes).orElse(this.includes);
            return this;
        }

        public Builder excludes(final List<String> excludes) {
            this.excludes = Optional.ofNullable(excludes).orElse(this.excludes);
            return this;
        }

        public Predicate<Path> build() {
            if (!this.includes.isEmpty() && !this.excludes.isEmpty())
                return matches(includes).and(not(matches(this.excludes)));

            if (!this.includes.isEmpty())
                return matches(includes);

            if (!this.excludes.isEmpty())
                return not(matches(this.excludes));

            return path -> true;
        }
    }

    private final List<String> syntaxAndPatterns;
    private final List<PathMatcher> matchers;

    /**
     * Creates a new {@link PathMatcherPredicate} instance.
     *
     * @param basePath a base path to chroot all patterns - may be {@code null}.
     * @param patterns a list of glob or regex expressions.
     */
    private PathMatcherPredicate(@Nullable final Path basePath, final List<String> patterns) {
        Objects.requireNonNull(patterns, "patterns cannot be null");
        this.syntaxAndPatterns = patterns.stream()
            .map(p -> {
                String syntaxAndPattern = p;
                if (!isPrefixWithSyntax(p)) {
                    String pattern;
                    if (basePath != null) {
                        pattern = basePath + mayAddLeadingSlash(p);
                    } else {
                        pattern = mayAddRecursiveMatch(p);
                    }
                    syntaxAndPattern = SYNTAX_GLOB + pattern;
                }
                return syntaxAndPattern;
            })
            .toList();
        FileSystem fs = FileSystems.getDefault();
        this.matchers = this.syntaxAndPatterns.stream().map(fs::getPathMatcher).toList();
    }

    private static String mayAddRecursiveMatch(final String p) {
        return p.matches("\\w+[\\s\\S]*") ? "**/" + p : p;
    }

    public List<String> syntaxAndPatterns() {
        return syntaxAndPatterns;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public boolean test(Path path) {
        return matchers.stream().anyMatch(p -> p.matches(path));
    }

    private static String mayAddLeadingSlash(final String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    public static boolean isPrefixWithSyntax(final String pattern) {
        return pattern.startsWith(SYNTAX_REGEX) | pattern.startsWith(SYNTAX_GLOB);
    }
}
