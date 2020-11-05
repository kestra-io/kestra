package org.kestra.cli;

import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import picocli.CommandLine;

import javax.inject.Inject;
import javax.inject.Singleton;

class VersionProvider implements CommandLine.IVersionProvider {
    private static org.kestra.core.utils.VersionProvider versionProvider;

    public String[] getVersion() {
        return new String[] { versionProvider.getVersion() };
    }

    @Singleton
    public static class ContextHelper {
        @Inject
        private org.kestra.core.utils.VersionProvider versionProvider;

        @EventListener
        void onStartup(final StartupEvent event) {
            VersionProvider.versionProvider = this.versionProvider;
        }
    }
}