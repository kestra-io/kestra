package io.kestra.core.runners;


import java.util.List;
import java.util.Optional;

public interface Checkpoints {
    List<io.kestra.core.checkpoint.Checkpoint> list();

    Optional<String> get(String key);

    void set(String key, String value);
}
