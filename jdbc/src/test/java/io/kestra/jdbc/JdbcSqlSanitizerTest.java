package io.kestra.jdbc;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderKeywordCase;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

class JdbcSqlSanitizerTest {
    private static final Settings SETTINGS = new Settings()
        .withRenderKeywordCase(RenderKeywordCase.UPPER)
        .withRenderFormatted(true);

    @Test
    void shouldCollapseInListToSameResultWhenElementCountDiffers() {
        // Given
        // built via the real jOOQ DSL rather than hand-written strings, so a jOOQ upgrade that
        // changes rendering breaks this test instead of silently invalidating it
        String threeElements = pg().select(field("value")).from(table("executions"))
            .where(field("state_current").in(List.of("A", "B", "C"))).getSQL();
        String fiveElements = pg().select(field("value")).from(table("executions"))
            .where(field("state_current").in(List.of("A", "B", "C", "D", "E"))).getSQL();

        // When
        String sanitizedThree = JdbcSqlSanitizer.sanitize(threeElements);
        String sanitizedFive = JdbcSqlSanitizer.sanitize(fiveElements);

        // Then
        // the actual cardinality property under test: a 3- and a 5-element list must produce the
        // *same* tag, regardless of the exact expected string
        assertThat(sanitizedThree).isEqualTo(sanitizedFive).isEqualTo("""
            SELECT value
            FROM executions
            WHERE state_current IN (?)""");
    }

    @Test
    void shouldLeaveInListUnchangedWhenItHasASingleElement() {
        // Given
        String sql = pg().select(field("value")).from(table("executions"))
            .where(field("state_current").in(List.of("A"))).getSQL();

        // When
        String sanitized = JdbcSqlSanitizer.sanitize(sql);

        // Then
        // this is what makes the pass idempotent: its own output must sanitize to itself
        assertThat(sanitized).isEqualTo(sql);
    }

    @Test
    void shouldCollapseNotInListWhenItHasMultipleElements() {
        // Given
        String sql = pg().select(field("value")).from(table("executions"))
            .where(field("state_current").notIn(List.of("A", "B"))).getSQL();

        // When / Then
        assertThat(JdbcSqlSanitizer.sanitize(sql)).isEqualTo("""
            SELECT value
            FROM executions
            WHERE state_current NOT IN (?)""");
    }

    @Test
    void shouldRedactAliasWhenItIsDashboardOrFixed() {
        // Given
        // "dash col" stands in for a user-authored dashboard column key; "metric_value" is one of
        // the codebase's own fixed aliases (AbstractJdbcMetricRepository) swept up by the same rule
        String sql = pg().select(field("state_current").as("dash col"), count().as("metric_value"))
            .from(table("executions")).getSQL();

        // When / Then
        assertThat(JdbcSqlSanitizer.sanitize(sql)).isEqualTo("""
            SELECT
              state_current AS "?",
              count(*) AS "?"
            FROM executions""");
    }

    @Test
    void shouldRedactOrderByColumnWhenItIsFreeText() {
        // Given
        String sql = pg().select(field("value")).from(table("executions"))
            .orderBy(field("user order col").desc()).limit(50).getSQL();

        // When / Then
        assertThat(JdbcSqlSanitizer.sanitize(sql)).isEqualTo("""
            SELECT value
            FROM executions
            ORDER BY ?
            FETCH NEXT ? ROWS ONLY""");
    }

    @Test
    void shouldCollapseInListWhenSqlIsLowercaseWithInlinedLiterals() {
        // Given
        // mirrors jdbc-postgres's PostgresLogRepositoryService, which builds this shape by hand:
        // lowercase keyword, inlined (not bound) literals, proving the literals' internal commas
        // and casts don't desync the paren-depth scan
        String in = pg().select().from(table("logs"))
            .where(DSL.condition("level in ('INFO'::log_level, 'WARN'::log_level, 'ERROR'::log_level)")).getSQL();
        String notIn = pg().select().from(table("logs"))
            .where(DSL.condition("level not in ('INFO'::log_level, 'WARN'::log_level)")).getSQL();

        // When / Then
        assertThat(JdbcSqlSanitizer.sanitize(in)).isEqualTo("""
            SELECT *
            FROM logs
            WHERE (level in (?))""");
        assertThat(JdbcSqlSanitizer.sanitize(notIn)).isEqualTo("""
            SELECT *
            FROM logs
            WHERE (level not in (?))""");
    }

    @Test
    void shouldLeaveQueryUnchangedWhenNothingIsUnbounded() {
        // Given
        String sql = pg().select(field("value")).from(table("queues"))
            .where(field("type").eq("x")).and(field("consumer_group").isNull()).getSQL();

        // When / Then
        assertThat(JdbcSqlSanitizer.sanitize(sql)).isEqualTo(sql);
    }

    @Test
    void shouldNotRedactWhenInKeywordIsNotFollowedByParenthesis() {
        // Given
        // regression test: the IN-list detector requires an immediately-following '(', otherwise
        // "AGAINST (? IN BOOLEAN MODE)" (a real shape used by MysqlRepository) would get mangled
        String sql = my().select(field("value")).from(table("t"))
            .where(DSL.condition("MATCH (fulltext) AGAINST (? IN BOOLEAN MODE)", "+ab*")).getSQL();

        // When / Then
        assertThat(JdbcSqlSanitizer.sanitize(sql)).isEqualTo(sql);
    }

    @Test
    void shouldRedactAliasWhenMysqlBacktickIsEscaped() {
        // Given
        String sql = my().select(field("state_current").as("a`b")).from(table("executions")).getSQL();

        // When / Then
        assertThat(sql).contains("`a``b`"); // sanity check on jOOQ's own doubled-backtick escaping
        assertThat(JdbcSqlSanitizer.sanitize(sql)).isEqualTo("""
            SELECT state_current AS `?`
            FROM executions""");
    }

    @Test
    void shouldRedactAliasWhenItContainsInKeywordAndParentheses() {
        // Given
        // the key adversarial case for order-insensitivity: an alias whose text itself looks like
        // "IN (...)" must not confuse the IN-list collapsing pass
        String sql = pg().select(field("state_current").as("x IN (a,b)")).from(table("executions"))
            .where(field("c").in(List.of("1", "2", "3"))).getSQL();

        // When / Then
        assertThat(JdbcSqlSanitizer.sanitize(sql)).isEqualTo("""
            SELECT state_current AS "?"
            FROM executions
            WHERE c IN (?)""");
    }

    @Test
    void shouldStopOrderByRedactionAtParenthesisWhenInsideDerivedTable() {
        // Given
        DSLContext pg = pg();
        String sql = pg.select(count()).from(
            pg.select(field("state_current").as("dash")).from(table("executions"))
                .where(field("a").in(List.of("1", "2")))
                .orderBy(field("weird col").asc())
                .asTable("cte")
        ).getSQL();

        // When / Then
        assertThat(JdbcSqlSanitizer.sanitize(sql)).isEqualTo("""
            SELECT count(*)
            FROM (
              SELECT state_current AS "?"
              FROM executions
              WHERE a IN (?)
              ORDER BY ?
            ) AS "?\"""");
    }

    @Test
    void shouldRedactOrderByColumnWhenItIsNamedLikeABoundaryKeyword() {
        // Given
        // regression test: without requiring at least one character of the sort list to be scanned
        // before checking for a boundary keyword, a column literally named "offset" would be
        // mistaken for the OFFSET pagination clause at position zero, yielding an empty (unredacted)
        // span - and the same bug would then also leak a free-text column merely starting with it
        String namedOffset = pg().select(field("value")).from(table("queues"))
            .orderBy(field("offset").asc()).getSQL();
        String startsWithOffset = pg().select(field("value")).from(table("queues"))
            .orderBy(field("offset xyz").asc()).limit(5).getSQL();

        // When / Then
        assertThat(JdbcSqlSanitizer.sanitize(namedOffset)).isEqualTo("""
            SELECT value
            FROM queues
            ORDER BY ?""");
        assertThat(JdbcSqlSanitizer.sanitize(startsWithOffset)).isEqualTo("""
            SELECT value
            FROM queues
            ORDER BY ?
            FETCH NEXT ? ROWS ONLY""");
    }

    @Test
    void shouldRedactOrderByWhenRenderedAsCaseExpressionOrOffsetLimit() {
        // Given
        // both cover AbstractJdbcRepository.sort()'s free-text API column: MySQL renders nullsLast()
        // as a CASE expression, Postgres renders offset+limit as separate OFFSET/FETCH clauses
        String mysqlNullsLast = my().select(field("value")).from(table("t"))
            .orderBy(field(DSL.name("my_free_col")).desc().nullsLast()).getSQL();
        String pgOffsetLimit = pg().select(field("value")).from(table("t"))
            .orderBy(field(DSL.name("my_free_col")).asc().nullsFirst()).limit(10).offset(20).getSQL();

        // When / Then
        assertThat(JdbcSqlSanitizer.sanitize(mysqlNullsLast)).isEqualTo("""
            SELECT value
            FROM t
            ORDER BY ?""");
        assertThat(JdbcSqlSanitizer.sanitize(pgOffsetLimit)).isEqualTo("""
            SELECT value
            FROM t
            ORDER BY ?
            OFFSET ? ROWS
            FETCH NEXT ? ROWS ONLY""");
    }

    @Test
    void shouldStopOrderByRedactionWhenForUpdateFollows() {
        // Given
        String sql = pg().select(field("value")).from(table("queues"))
            .orderBy(field("a").asc()).limit(1).forUpdate().skipLocked().getSQL();

        // When / Then
        assertThat(JdbcSqlSanitizer.sanitize(sql)).isEqualTo("""
            SELECT value
            FROM queues
            ORDER BY ?
            FETCH NEXT ? ROWS ONLY
            FOR UPDATE SKIP LOCKED""");
    }

    @Test
    void shouldPreserveSelectWhenInListIsASubquery() {
        // Given
        // no call site in the codebase uses this form today, but preserving it costs one keyword
        // check and keeps the tag useful if that ever changes; the inner list-based IN still collapses
        DSLContext pg = pg();
        String sql = pg.select(field("value")).from(table("queues"))
            .where(field("b").in(
                pg.select(field("c")).from(table("executions")).where(field("d").in(List.of(1, 2, 3)))
            )).getSQL();

        // When / Then
        assertThat(JdbcSqlSanitizer.sanitize(sql)).isEqualTo("""
            SELECT value
            FROM queues
            WHERE b IN (
              SELECT c
              FROM executions
              WHERE d IN (?)
            )""");
    }

    @Test
    void shouldSanitizeAllOccurrencesWhenQueryHasMultipleInListsAndAliases() {
        // Given
        String sql = pg().select(field("state_current").as("dash1"), count().as("metric_value"))
            .from(table("executions"))
            .where(field("a").in(List.of("1", "2", "3")))
            .and(field("b").notIn(List.of("x", "y")))
            .groupBy(field("state_current"))
            .orderBy(field("user sort col").asc())
            .limit(20).getSQL();

        // When / Then
        assertThat(JdbcSqlSanitizer.sanitize(sql)).isEqualTo("""
            SELECT
              state_current AS "?",
              count(*) AS "?"
            FROM executions
            WHERE (
              a IN (?)
              AND b NOT IN (?)
            )
            GROUP BY state_current
            ORDER BY ?
            FETCH NEXT ? ROWS ONLY""");
    }

    @Test
    void shouldBeIdempotentAndOrderInsensitiveForEveryFixture() {
        // Given
        List<String> fixtures = List.of(
            pg().select(field("value")).from(table("executions")).where(field("state_current").in(List.of("A", "B", "C"))).getSQL(),
            pg().select(field("state_current").as("dash col"), count().as("metric_value")).from(table("executions")).getSQL(),
            pg().select(field("value")).from(table("executions")).orderBy(field("user order col").desc()).limit(50).getSQL(),
            pg().select(field("state_current").as("x IN (a,b)")).from(table("executions")).where(field("c").in(List.of("1", "2", "3"))).getSQL(),
            my().select(field("state_current").as("a`b")).from(table("executions")).getSQL(),
            pg().select().from(table("logs")).where(DSL.condition("level in ('INFO'::log_level, 'WARN'::log_level)")).getSQL()
        );

        for (String sql : fixtures) {
            // When
            String sanitized = JdbcSqlSanitizer.sanitize(sql);
            String reversedOrder = JdbcSqlSanitizer.collapseInLists(
                JdbcSqlSanitizer.redactQuotedIdentifiers(
                    JdbcSqlSanitizer.redactOrderByColumns(sql)));

            // Then
            assertThat(JdbcSqlSanitizer.sanitize(sanitized)).as("idempotent for: %s", sql).isEqualTo(sanitized);
            assertThat(reversedOrder).as("order-insensitive for: %s", sql).isEqualTo(sanitized);
        }
    }

    @Test
    void shouldFailOpenWhenSqlIsMalformed() {
        // Given / When / Then
        // no exception, no infinite loop, and the unprocessed remainder is left as-is rather than
        // thrown away - a slightly less sanitized tag is a far smaller problem than losing the metric
        assertThat(JdbcSqlSanitizer.sanitize(null)).isNull();
        assertThat(JdbcSqlSanitizer.sanitize("")).isEmpty();
        assertThat(JdbcSqlSanitizer.sanitize("SELECT * FROM x WHERE a = 'oops")).isEqualTo("SELECT * FROM x WHERE a = 'oops");
        assertThat(JdbcSqlSanitizer.sanitize("SELECT * FROM x WHERE a IN")).isEqualTo("SELECT * FROM x WHERE a IN");
        assertThat(JdbcSqlSanitizer.sanitize("SELECT * FROM x ORDER BY")).isEqualTo("SELECT * FROM x ORDER BY");
        assertThat(JdbcSqlSanitizer.sanitize("SELECT a FROM (x ORDER BY b")).isEqualTo("SELECT a FROM (x ORDER BY ?");
    }

    @Test
    void shouldProduceDifferentTagsWhenQueryShapesDiffer() {
        // Given
        String bounded = pg().select(field("value")).from(table("queues"))
            .where(field("type").eq("x")).getSQL();
        String withOrderBy = pg().select(field("value")).from(table("queues"))
            .orderBy(field("offset").asc()).getSQL();

        // When / Then
        // guards against the sanitizer being too aggressive and flattening every query into one
        // indistinguishable tag
        assertThat(JdbcSqlSanitizer.sanitize(bounded)).isNotEqualTo(JdbcSqlSanitizer.sanitize(withOrderBy));
    }

    private static DSLContext pg() {
        return DSL.using(SQLDialect.POSTGRES, SETTINGS);
    }

    private static DSLContext my() {
        return DSL.using(SQLDialect.MYSQL, SETTINGS);
    }
}
