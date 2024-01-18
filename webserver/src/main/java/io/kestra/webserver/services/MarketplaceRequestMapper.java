package io.kestra.webserver.services;

import jakarta.inject.Singleton;
import io.kestra.webserver.controllers.domain.MarketplaceRequestType;

@Singleton
// This mapper is almost a no-op but is necessary to ease testing MarketplaceFilter
public class MarketplaceRequestMapper {
    public String url(MarketplaceRequestType type) {
        return type.getUrl();
    }

    public String resourceBaseUrl(String publisher) {
        return "https://" + publisher + ".vscode-unpkg.net";
    }
}
