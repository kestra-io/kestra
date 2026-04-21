import {describe, expect, it} from "vitest";

import {
    getPluginSuggestionSegments,
    pluginSuggestionRank,
    pluginSuggestionSortText,
} from "../../../src/composables/monaco/languages/yamlLanguageConfigurator";

describe("YamlLanguageConfigurator ranking", () => {
    it("splits plugin labels into searchable segments", () => {
        expect(getPluginSuggestionSegments("io.kestra.plugin.core.execution.PurgeExecutions"))
            .toEqual(["io", "kestra", "plugin", "core", "execution", "purgeexecutions"]);
    });

    it("prioritizes last-segment prefix matches for purge-like searches", () => {
        const purgeRank = pluginSuggestionRank(
            "io.kestra.plugin.core.execution.PurgeExecutions",
            "purge",
        );
        const executionRank = pluginSuggestionRank(
            "io.kestra.plugin.core.execution.ExecutionLabels",
            "purge",
        );

        expect(purgeRank[0]).toBeLessThan(executionRank[0]);
        expect(pluginSuggestionSortText(
            "io.kestra.plugin.core.execution.PurgeExecutions",
            "purge",
        ) < pluginSuggestionSortText(
            "io.kestra.plugin.core.execution.ExecutionLabels",
            "purge",
        )).toBe(true);
    });

    it("prioritizes exact final-segment matches over earlier-segment exact matches", () => {
        expect(pluginSuggestionSortText(
            "io.kestra.plugin.core.condition.If",
            "if",
        ) < pluginSuggestionSortText(
            "io.kestra.plugin.if.core.Condition",
            "if",
        )).toBe(true);
    });

    it("prefers exact segment matches over generic substring matches for kestra", () => {
        expect(pluginSuggestionSortText(
            "io.kestra.plugin.core.kestra.Run",
            "kestra",
        ) < pluginSuggestionSortText(
            "io.kestra.plugin.core.MyKestraTask",
            "kestra",
        )).toBe(true);
    });
});
