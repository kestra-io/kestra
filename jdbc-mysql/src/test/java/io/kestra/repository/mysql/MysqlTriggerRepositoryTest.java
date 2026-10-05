package io.kestra.repository.mysql;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Field;
import io.kestra.core.models.QueryFilter.Op;
import io.kestra.core.repositories.AbstractTriggerRepositoryTest;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.data.model.Pageable;

import static org.assertj.core.api.Assertions.assertThat;

public class MysqlTriggerRepositoryTest extends AbstractTriggerRepositoryTest {

    @Test
    void shouldSkipSubstringMatchesWhenTheFulltextIndexFindsAMatch() {
        // Given a flow id starting with the searched word, and one only containing it
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        triggerStateStore.save(trigger(tenant, "search_alpha"));
        triggerStateStore.save(trigger(tenant, "my_search_beta"));

        // When searching for that word
        ArrayListTotal<TriggerState> entries = search(tenant, "search", Op.EQUALS);

        // Then only the FULLTEXT match is returned, so the LIKE scan was not run
        assertThat(entries).extracting(TriggerState::getFlowId).containsExactly("search_alpha");
        assertThat(entries.getTotal()).isEqualTo(1);
    }

    @Test
    void shouldExcludeSubstringMatchesWhenTheSearchIsNegated() {
        // Given flow ids matching the searched word through the FULLTEXT index, through a substring, and not at all
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        triggerStateStore.save(trigger(tenant, "search_alpha"));
        triggerStateStore.save(trigger(tenant, "my_search_beta"));
        triggerStateStore.save(trigger(tenant, "other_flow"));

        // When excluding that word
        ArrayListTotal<TriggerState> entries = search(tenant, "search", Op.NOT_EQUALS);

        // Then both kinds of match are excluded
        assertThat(entries).extracting(TriggerState::getFlowId).containsExactly("other_flow");
        assertThat(entries.getTotal()).isEqualTo(1);
    }

    private ArrayListTotal<TriggerState> search(String tenant, String query, Op operation) {
        return triggerRepository.find(
            Pageable.from(1, 10),
            tenant,
            List.of(QueryFilter.builder().field(Field.QUERY).value(query).operation(operation).build())
        );
    }

    private static TriggerState trigger(String tenant, String flowId) {
        return TriggerState.builder()
            .tenantId(tenant)
            .namespace("io.kestra.unittest")
            .flowId(flowId)
            .triggerId("schedule")
            .nextEvaluationDate(Instant.now())
            .workerId("workerId")
            .build();
    }
}
