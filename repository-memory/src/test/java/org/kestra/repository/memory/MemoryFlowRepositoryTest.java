package org.kestra.repository.memory;

import com.devskiller.friendly_id.FriendlyId;
import io.micronaut.core.value.ValueException;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.annotation.MicronautTest;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.repositories.AbstractFlowRepositoryTest;
import org.kestra.core.repositories.ArrayListTotal;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import java.util.ArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
public class MemoryFlowRepositoryTest extends AbstractFlowRepositoryTest {

    @Inject
    MemoryFlowRepository memoryFlowRepository;
}
