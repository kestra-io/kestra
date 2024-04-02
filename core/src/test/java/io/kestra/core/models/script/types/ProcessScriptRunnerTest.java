package io.kestra.core.models.script.types;

import io.kestra.core.models.script.AbstractScriptRunnerTest;
import io.kestra.core.models.script.ScriptRunner;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

class ProcessScriptRunnerTest extends AbstractScriptRunnerTest {

    @Override
    protected ScriptRunner scriptRunner() {
        return new ProcessScriptRunner();
    }
}