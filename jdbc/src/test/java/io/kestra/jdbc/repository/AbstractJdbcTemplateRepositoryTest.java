package io.kestra.jdbc.repository;

import io.kestra.core.models.templates.Template;
import io.kestra.jdbc.JdbcTestUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public abstract class AbstractJdbcTemplateRepositoryTest extends io.kestra.core.repositories.AbstractTemplateRepositoryTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Test
    void find() {
        templateRepository.create(builder("io.kestra.unitest").build());
        templateRepository.create(builder("com.kestra.test").build());

        List<Template> save = templateRepository.find(Pageable.from(1, 10, Sort.UNSORTED), null, null);
        assertThat(save.size(), is(2));

        save = templateRepository.find(Pageable.from(1, 10, Sort.UNSORTED), "kestra", "com");
        assertThat(save.size(), is(1));

        save = templateRepository.find(Pageable.from(1, 10, Sort.of(Sort.Order.asc("id"))), "kestra unit", null);
        assertThat(save.size(), is(1));
    }

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
        super.init();
    }
}