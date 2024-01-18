package io.kestra.repository.h2;

import io.kestra.jdbc.repository.AbstractJdbcFlowRepositoryTest;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.net.URISyntaxException;

public class H2FlowRepositoryTest extends AbstractJdbcFlowRepositoryTest {

    // On H2 we must reset the database and init the flow repository on the same method.
    // That's why the setup is overridden to do noting and the init will do the setup.
    @Override
    protected void setup() {
    }

    @Override
    @BeforeEach // on H2 we must reset the
    protected void init() throws IOException, URISyntaxException {
        super.setup();
        super.init();
    }
}
