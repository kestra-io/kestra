package io.kestra.core.models.tasks;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.kestra.core.models.property.Property;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.core.flow.Approval;

import static org.assertj.core.api.Assertions.assertThat;

class TaskForExecutionTest {
    @Test
    void shouldExposeTheReviewSettingsOfAnApproval() {
        Approval approval = Approval.builder()
            .id("approval")
            .type(Approval.class.getName())
            .commentRequired(Property.ofValue(Approval.CommentRequired.ON_DENY))
            .decisions(Approval.Decisions.builder().approve(Property.ofValue("Ship it")).deny(Property.ofValue("Hold")).build())
            .build();

        JsonNode json = JacksonMapper.ofJson().valueToTree(TaskForExecution.of(approval));

        assertThat(json.get("commentRequired").asText()).isEqualTo("ON_DENY");
        assertThat(json.get("decisions").get("approve").asText()).isEqualTo("Ship it");
        assertThat(json.get("decisions").get("deny").asText()).isEqualTo("Hold");
    }
}
