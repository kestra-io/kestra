package io.kestra.jdbc.repository;

import io.kestra.core.models.templates.Template;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractJdbcTemplateRepositoryTest extends io.kestra.core.repositories.AbstractTemplateRepositoryTest {

    @Test
    void find() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        templateRepository.create(builder(tenant, "io.kestra.unitest").build());
        templateRepository.create(builder(tenant, "com.kestra.test").build());

        List<Template> save = templateRepository.find(Pageable.from(1, 10, Sort.UNSORTED), null, tenant, null);
        assertThat(save.size()).isEqualTo(2);

        save = templateRepository.find(Pageable.from(1, 10, Sort.UNSORTED), "kestra", tenant, "com");
        assertThat(save.size()).isEqualTo(1);

        save = templateRepository.find(Pageable.from(1, 10, Sort.of(Sort.Order.asc("id"))), "kestra unit", tenant, null);
        assertThat(save.size()).isEqualTo(1);
    }

}