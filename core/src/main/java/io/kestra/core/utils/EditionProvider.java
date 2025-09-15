package io.kestra.core.utils;

import jakarta.inject.Singleton;

@Singleton
public class EditionProvider {
    public Edition get() {
        return Edition.OSS;
    }

    public enum Edition {
        OSS,
        EE
    }
}
