package org.floworc.repository.memory;

import com.devskiller.friendly_id.FriendlyId;
import io.micronaut.core.value.ValueException;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.annotation.MicronautTest;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.repositories.AbstractFlowRepositoryTest;
import org.floworc.core.repositories.ArrayListTotal;
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

    @Test
    void find() {
        Flow flow1 = Flow.builder()
            .id(FriendlyId.createFriendlyId())
            .revision(1)
            .namespace("org.floworc.unittest.flow.find")
            .build();
        memoryFlowRepository.save(flow1);
        Flow flow2 = Flow.builder()
            .id(FriendlyId.createFriendlyId())
            .revision(1)
            .namespace("org.floworc.unittest.flow.find")
            .build();
        memoryFlowRepository.save(flow2);

        ArrayListTotal<Flow> result = memoryFlowRepository.findByNamespace("org.floworc.unittest.flow.find", Pageable.from(1, 5));
        assertThat(result.size(), is(2));
        assertThat(result, hasItem(flow1));
        assertThat(result, hasItem(flow2));

        var testFetch = new ArrayList<Flow>();
        result = memoryFlowRepository.findByNamespace("org.floworc.unittest.flow.find", Pageable.from(1, 1));
        assertThat(result.size(), is(1));
        testFetch.addAll(result);
        result = memoryFlowRepository.findByNamespace("org.floworc.unittest.flow.find", Pageable.from(2, 1));
        assertThat(result.size(), is(1));
        testFetch.addAll(result);
        assertThat(testFetch, hasItem(flow1));
        assertThat(testFetch, hasItem(flow2));

        ValueException e = assertThrows(ValueException.class, () -> {
            ArrayListTotal<Flow> exceptionResult = memoryFlowRepository.findByNamespace("org.floworc.unittest.flow.find", Pageable.from(0, 1));
        });
        assertThat(e.getMessage(), is("Page cannot be < 1"));
    }


}
