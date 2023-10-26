package io.kestra.core.runners;

import java.util.Set;

public record ExecutionsRunning(String flowUid, Set<String> executionIds) { }
