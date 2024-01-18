package io.kestra.webserver.services;

import io.kestra.webserver.controllers.domain.MarketplaceRequestType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class MarketplaceRequestMapperTest {
    private MarketplaceRequestMapper marketplaceRequestMapper = new MarketplaceRequestMapper();

    @Test
    void testUrl() {
        MarketplaceRequestType[] marketplaceRequestTypes = MarketplaceRequestType.values();
        assertThat(
            Arrays.stream(marketplaceRequestTypes).map(MarketplaceRequestType::getUrl).toArray(String[]::new),
            is(Arrays.stream(marketplaceRequestTypes).map(marketplaceRequestMapper::url).toArray(String[]::new))
        );
    }

    @Test
    void testResourceBaseUrl() {
        assertThat(marketplaceRequestMapper.resourceBaseUrl("my-publisher"), is("https://my-publisher.vscode-unpkg.net"));
    }
}
