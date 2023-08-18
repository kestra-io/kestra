package io.kestra.repository.memory;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.util.StringUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.kestra.core.repositories.AbstractTemplateRepositoryTest;

@MicronautTest
@Property(name = "kestra.templates.enabled", value = StringUtils.TRUE)
public class MemoryTemplateRepositoryTest extends AbstractTemplateRepositoryTest {

}
