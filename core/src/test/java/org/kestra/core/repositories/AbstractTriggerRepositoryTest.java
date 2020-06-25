package org.kestra.core.repositories;

import com.devskiller.friendly_id.FriendlyId;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.triggers.Trigger;

import java.time.ZonedDateTime;
import java.util.Optional;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
public abstract class AbstractTriggerRepositoryTest {
    @Inject
    protected TriggerRepositoryInterface triggerRepository;

    private static Trigger.TriggerBuilder<?, ?> trigger() {
        return Trigger.builder()
            .flowId(FriendlyId.createFriendlyId())
            .namespace("org.kestra.unittest")
            .flowRevision(1)
            .triggerId(FriendlyId.createFriendlyId())
            .executionId(FriendlyId.createFriendlyId())
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

        save = triggerRepository.save(builder.executionId(FriendlyId.createFriendlyId()).build());

        find = triggerRepository.findLast(save);

        assertThat(find.isPresent(), is(true));
        assertThat(find.get().getExecutionId(), is(save.getExecutionId()));
    }
}
