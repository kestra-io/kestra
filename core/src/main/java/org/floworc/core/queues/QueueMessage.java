package org.floworc.core.queues;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueueMessage <T> {
    private String key;

    private T body;


}
