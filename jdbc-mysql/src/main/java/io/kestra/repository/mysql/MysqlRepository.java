package io.kestra.repository.mysql;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jooq.Condition;
import org.jooq.Context;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.RecordMapper;
import org.jooq.Result;
import org.jooq.Select;
import org.jooq.SelectConditionStep;
import org.jooq.VisitContext;
import org.jooq.VisitListener;
import org.jooq.impl.CustomCondition;
import org.jooq.impl.DSL;
import org.jooq.impl.QOM;

import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.jdbc.AbstractJdbcRepository;
import io.kestra.jdbc.JdbcTableConfig;
import io.kestra.jdbc.JooqDSLContextWrapper;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.Sort.Order;
import jakarta.inject.Inject;

@SuppressWarnings("this-escape")
@Requires(condition = MysqlRepository.MysqlCondition.class)
@Requires(property = "kestra.server-type", notEquals = "WORKER")
@EachBean(JdbcTableConfig.class)
public class MysqlRepository<T> extends AbstractJdbcRepository<T> {

    @Inject
    public MysqlRepository(@Parameter JdbcTableConfig jdbcTableConfig,
        JooqDSLContextWrapper dslContextWrapper) {
        super(jdbcTableConfig, dslContextWrapper);
        this.table = DSL.table(DSL.quotedName(this.getTable().getName()));
    }

    /** {@inheritDoc} **/
    @Override
    public Condition fullTextCondition(List<String> fields, String query) {
        if (query == null || query.equals("*")) {
            return DSL.noCondition();
        }

        String escaped = escapeForLike(query);
        String pattern = "%" + escaped + "%";

        Condition likeCondition = DSL.falseCondition();
        for (String fieldName : fields) {
            Field<String> f = DSL.field(fieldName, String.class);
            // COALESCE ensures NULL fields evaluate to false rather than NULL,
            // so NOT(...LIKE...) works correctly for nullable columns like execution_id.
            likeCondition = likeCondition.or(DSL.coalesce(f, DSL.inline("")).like(pattern, '\\'));
        }

        // Split like the InnoDB parser, which keeps '_' inside words, so an id like 'my_flow' is matched as one word.
        String booleanQuery = Arrays.stream(query.split("[^\\p{L}\\p{N}_]+"))
            .filter(s -> s.length() >= 3)
            .map(s -> "+" + s + "*")
            .collect(Collectors.joining(" "));

        if (booleanQuery.isEmpty()) {
            return likeCondition;
        }

        Condition fulltextCondition = DSL.condition(
            "MATCH (" + String.join(", ", fields) + ") AGAINST (? IN BOOLEAN MODE)",
            booleanQuery
        );

        return new FullTextCondition(fulltextCondition, likeCondition);
    }

    private static String escapeForLike(String s) {
        return s
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }

    @Override
    public <R extends Record, E> ArrayListTotal<E> fetchPage(DSLContext context, SelectConditionStep<R> select, Pageable pageable, RecordMapper<R, E> mapper) {
        // The LIKE scan can't use an index, so it only runs when the FULLTEXT match finds nothing.
        AtomicBoolean hasFullTextCondition = new AtomicBoolean();
        DSLContext pageContext = renderFullTextConditionAs(context, condition -> condition.fulltext, hasFullTextCondition);
        int rows = pageContext.fetchCount(select);
        if (rows == 0 && hasFullTextCondition.get()) {
            pageContext = renderFullTextConditionAs(context, condition -> condition.like, hasFullTextCondition);
            rows = pageContext.fetchCount(select);
        }

        Result<R> records = pageContext.fetch(this.pageable(select, pageable));
        return new ArrayListTotal<>(records.map(mapper), rows);
    }

    private static DSLContext renderFullTextConditionAs(DSLContext context, Function<FullTextCondition, Condition> part, AtomicBoolean rendered) {
        return DSL.using(context.configuration().deriveAppending(VisitListener.onVisitStart(visit ->
        {
            if (visit.queryPart() instanceof FullTextCondition condition && isRequiredForEveryRow(visit)) {
                visit.queryPart(part.apply(condition));
                rendered.set(true);
            }
        })));
    }

    // Under NOT or OR, the row count no longer tells whether the FULLTEXT match found anything, so both parts are kept.
    private static boolean isRequiredForEveryRow(VisitContext visit) {
        return Arrays.stream(visit.queryParts()).noneMatch(part -> part instanceof QOM.Not || part instanceof QOM.Or || part instanceof QOM.Xor);
    }

    @Override
    public <R extends Record> Select<R> buildQuery(DSLContext context, SelectConditionStep<R> select, String orderField) {
        return this.sort(select, Pageable.from(Sort.of(Order.asc(orderField))));
    }

    public Field<Integer> weekFromTimestamp(Field<Timestamp> timestampField) {
        // DAYOFWEEK > 5 means you have less than 3 days in the first week of the year so we choose mode 2 (see https://www.w3resource.com/mysql/date-and-time-functions/mysql-week-function.php)
        return DSL.when(
            DSL.field("DAYOFWEEK(CONCAT(YEAR({0}), '-01-01')) > 5", Boolean.class, timestampField),
            DSL.field("WEEK({0}, 2)", Integer.class, timestampField)
        ).otherwise(DSL.field("WEEK({0}, 3)", Integer.class, timestampField));
    }

    static class MysqlCondition implements io.micronaut.context.condition.Condition {
        @Override
        public boolean matches(ConditionContext context) {
            return ((Optional<String>) context.get("kestra.repository.type", String.class)).map(it -> "mysql".equals(it)).orElse(false);
        }
    }

    /**
     * Renders as the FULLTEXT match OR the LIKE scan, unless {@link #fetchPage} renders one part alone.
     */
    private static final class FullTextCondition extends CustomCondition {
        private final Condition fulltext;
        private final Condition like;

        private FullTextCondition(Condition fulltext, Condition like) {
            this.fulltext = fulltext;
            this.like = like;
        }

        @Override
        public void accept(Context<?> ctx) {
            ctx.visit(fulltext.or(like));
        }
    }
}
