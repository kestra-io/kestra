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
        ArrayListTotal<TriggerState> entries = find(tenant, query("search", Op.EQUALS));

        // Then only the FULLTEXT match is returned, so the LIKE scan was not run
        assertThat(entries).extracting(TriggerState::getFlowId).containsExactly("search_alpha");
        assertThat(entries.getTotal()).isEqualTo(1);
    }

    @Test
    void shouldMatchAnUnderscoredFlowIdAsOneWord() {
        // Given the searched flow id, and another flow id starting with its last part
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        triggerStateStore.save(trigger(tenant, "my_flow"));
        triggerStateStore.save(trigger(tenant, "flow_daily"));

        // When searching for that flow id
        ArrayListTotal<TriggerState> entries = find(tenant, query("my_flow", Op.EQUALS));

        // Then only that flow id is returned
        assertThat(entries).extracting(TriggerState::getFlowId).containsExactly("my_flow");
    }

    @Test
    void shouldExcludeSubstringMatchesWhenTheSearchIsNegated() {
        // Given flow ids matching the searched word through the FULLTEXT index, through a substring, and not at all
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        triggerStateStore.save(trigger(tenant, "search_alpha"));
        triggerStateStore.save(trigger(tenant, "my_search_beta"));
        triggerStateStore.save(trigger(tenant, "other_flow"));

        // When excluding that word
        ArrayListTotal<TriggerState> entries = find(tenant, query("search", Op.NOT_EQUALS));

        // Then both kinds of match are excluded
        assertThat(entries).extracting(TriggerState::getFlowId).containsExactly("other_flow");
        assertThat(entries.getTotal()).isEqualTo(1);
    }

    @Test
    void shouldKeepSubstringMatchesWhenTheSearchIsInAnOrGroup() {
        // Given a flow id only containing the searched word, one matching the other branch of the OR, and one matching neither
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        triggerStateStore.save(trigger(tenant, "my_search_beta"));
        triggerStateStore.save(trigger(tenant, "other_flow"));
        triggerStateStore.save(trigger(tenant, "unrelated_flow"));

        // When searching for that word OR that other flow id
        QueryFilter otherFlow = QueryFilter.builder().field(Field.FLOW_ID).value("other_flow").operation(Op.EQUALS).build();
        QueryFilter searchOrOtherFlow = QueryFilter.builder().logical(QueryFilter.Logical.OR).children(List.of(query("search", Op.EQUALS), otherFlow)).build();
        ArrayListTotal<TriggerState> entries = find(tenant, searchOrOtherFlow);

        // Then the substring match is still returned with the other branch
        assertThat(entries).extracting(TriggerState::getFlowId).containsExactlyInAnyOrder("my_search_beta", "other_flow");
    }

    private ArrayListTotal<TriggerState> find(String tenant, QueryFilter filter) {
        return triggerRepository.find(Pageable.from(1, 10), tenant, List.of(filter));
    }

    private static QueryFilter query(String value, Op operation) {
        return QueryFilter.builder().field(Field.QUERY).value(value).operation(operation).build();
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
