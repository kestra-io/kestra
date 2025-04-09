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

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractJdbcTemplateRepositoryTest extends io.kestra.core.repositories.AbstractTemplateRepositoryTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Test
    void find() {
        templateRepository.create(builder("io.kestra.unitest").build());
        templateRepository.create(builder("com.kestra.test").build());

        List<Template> save = templateRepository.find(Pageable.from(1, 10, Sort.UNSORTED), null, null, null);
        assertThat(save.size()).isEqualTo(2);

        save = templateRepository.find(Pageable.from(1, 10, Sort.UNSORTED), "kestra", null, "com");
        assertThat(save.size()).isEqualTo(1);

        save = templateRepository.find(Pageable.from(1, 10, Sort.of(Sort.Order.asc("id"))), "kestra unit", null, null);
        assertThat(save.size()).isEqualTo(1);
    }

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
        super.init();
    }
}