package io.kestra.runner.h2;

import io.kestra.jdbc.JdbcTestUtils;

import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;
import lombok.SneakyThrows;

@Singleton
@Replaces(JdbcTestUtils.class)
public class H2JdbcTestUtils extends JdbcTestUtils {
    @SneakyThrows
    public void drop() {
        //        dslContextWrapper.transaction((configuration) -> {
        //            DSLContext dslContext = DSL.using(configuration);
        //
        //            dslContext
        //                .meta()
        //                .getTables()
        //                .stream()
        //                .filter(throwPredicate(table -> (table.getSchema().getName().equals("FT"))))
        //                .filter(table -> !table.getName().equals("INDEXES"))
        //                .forEach(t -> dslContext.truncate(t).execute());
        //        });

        super.drop();
    }
}
