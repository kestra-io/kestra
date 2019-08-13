package org.floworc.core.tasks;

import org.junit.jupiter.api.Test;
import org.floworc.core.runners.types.StandAloneRunner;
import org.floworc.core.tasks.scripts.Bash;

class BashTest {

    @Test
    void run() throws Exception {
        Bash bash = new Bash(
            new String[]{"sleep 1", "curl www.google.fr > /dev/null", "echo 0", "sleep 1", "echo 1"}
        );
        bash.run();
    }
}