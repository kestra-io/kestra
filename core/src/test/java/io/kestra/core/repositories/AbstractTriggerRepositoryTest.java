package io.kestra.core.repositories;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.utils.IdUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest(transactional = false)
public abstract class AbstractTriggerRepositoryTest {
    @Inject
    protected TriggerRepositoryInterface triggerRepository;

    private static Trigger.TriggerBuilder<?, ?> trigger() {
        return Trigger.builder()
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .flowRevision(1)
            .triggerId(IdUtils.create())
            .executionId(IdUtils.create())
            .date(ZonedDateTime.now());
    }

    @Test
    void all() {
        Trigger.TriggerBuilder<?, ?> builder = trigger();

        Optional<Trigger> find = triggerRepository.findLast(builder.build());
        assertThat(find.isPresent(), is(false));


        Trigger save = triggerRepository.save(builder.build());

        find = triggerRepository.findLast(save);

        assertThat(find.isPresent(), is(true));
        assertThat(find.get().getExecutionId(), is(save.getExecutionId()));

        save = triggerRepository.save(builder.executionId(IdUtils.create()).build());

        find = triggerRepository.findLast(save);

        assertThat(find.isPresent(), is(true));
        assertThat(find.get().getExecutionId(), is(save.getExecutionId()));


        triggerRepository.save(trigger().build());
        triggerRepository.save(trigger().build());
        triggerRepository.save(trigger().build());

        List<Trigger> all = triggerRepository.findAll();

        assertThat(all.size(), is(4));
    }
}
