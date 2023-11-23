package io.kestra.webserver.controllers.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

public enum MarketplaceRequestType {
        NLS("https://vscode-unpkg.net/_lp"),
        SERVICE("https://marketplace.visualstudio.com/_apis/public/gallery"),
        SEARCH("https://marketplace.visualstudio.com/_apis/public/gallery/searchrelevancy/extensionquery"),
        SERVICEPPE("https://marketplace.vsallin.net/_apis/public/gallery"),
        CACHE("https://vscode.blob.core.windows.net/gallery/index"),
        ITEM("https://marketplace.visualstudio.com/items"),
        PUBLISHER("https://marketplace.visualstudio.com/publishers"),
        CONTROL("https://az764295.vo.msecnd.net/extensions/marketplace.json");

        @Getter
        private final String url;

        MarketplaceRequestType(String url) {
            this.url = url;
        }

        @JsonCreator
        public static MarketplaceRequestType fromString(String key) {
            return key == null
                ? null
                : MarketplaceRequestType.valueOf(key.toUpperCase());
        }
    }