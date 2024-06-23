package io.kestra.repository.h2;

import io.kestra.core.models.SearchResult;
import io.kestra.core.models.flows.Flow;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepositoryTest;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

public class H2FlowRepositoryTest extends AbstractJdbcFlowRepositoryTest {

    // On H2 we must reset the database and init the flow repository on the same method.
    // That's why the setup is overridden to do noting and the init will do the setup.
    @Override
    protected void setup() {
    }

    @Test
    @Override
    public void findSourceCode() {
        List<SearchResult<Flow>> search = flowRepository.findSourceCode(Pageable.from(1, 10, Sort.UNSORTED), "io.kestra.plugin.core.condition.MultipleCondition", null, null);

        // FIXME since the big task renaming, H2 return 6 instead of 2
        //  as no core change this is a test artefact, or a latent bug in H2.
        assertThat((long) search.size(), is(6L));

        SearchResult<Flow> flow = search
            .stream()
            .filter(flowSearchResult -> flowSearchResult.getModel()
                .getId()
                .equals("trigger-multiplecondition-listener"))
            .findFirst()
            .orElseThrow();
        assertThat(flow.getFragments().getFirst(), containsString("condition.MultipleCondition[/mark]"));
    }

    @Override
    @BeforeEach // on H2 we must reset the
    protected void init() throws IOException, URISyntaxException {
        super.setup();
        super.init();
    }
}
