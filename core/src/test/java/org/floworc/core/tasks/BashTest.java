package org.floworc.core.tasks;

import org.floworc.core.tasks.scripts.Bash;
import org.junit.jupiter.api.Test;

class BashTest {
    @Test
    void run() throws Exception {
        Bash bash = new Bash(
            new String[]{"sleep 1", "curl www.google.fr > /dev/null", "echo 0", "sleep 1", "echo 1"}
        );
        bash.run();
    }
}