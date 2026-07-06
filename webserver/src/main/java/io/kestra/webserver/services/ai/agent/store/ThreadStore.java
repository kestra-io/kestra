package io.kestra.webserver.services.ai.agent.store;

import java.util.Optional;

import io.kestra.webserver.services.ai.agent.domain.Thread;

public interface ThreadStore {
    Thread create(Thread thread);

    Optional<Thread> find(String tenant, String uid);

    Thread save(Thread thread);
}
