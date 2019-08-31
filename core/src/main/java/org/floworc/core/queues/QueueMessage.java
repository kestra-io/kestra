package org.floworc.core.queues;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class QueueMessage <T> {
    private String key;

    private T body;
}
