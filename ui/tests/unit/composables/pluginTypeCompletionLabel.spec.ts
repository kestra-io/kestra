import {describe, expect, it} from "vitest";
import {splitPluginTypeLabel} from "../../../src/composables/monaco/languages/pluginTypeCompletionLabel";

describe("splitPluginTypeLabel", () => {
    it.each([
        [
            "io.kestra.plugin.ee.apps.execution.blocks.CreateExecutionButton",
            {label: "CreateExecutionButton", description: "io.kestra.plugin.ee.apps.execution.blocks"},
        ],
        [
            "io.kestra.plugin.ee.apps.execution.blocks.CreateExecutionForm",
            {label: "CreateExecutionForm", description: "io.kestra.plugin.ee.apps.execution.blocks"},
        ],
        [
            "io.kestra.plugin.core.log.Log",
            {label: "Log", description: "io.kestra.plugin.core.log"},
        ],
    ])("splits %j into its class name and package", (label, expected) => {
        expect(splitPluginTypeLabel(label)).toEqual(expected);
    });

    it.each([
        "type",
        "concurrency",
        "MAX_DURATION",
        "PT5M",
        "io.kestra.plugin.core.log",
        "Log",
        "2026-09-23",
    ])("leaves %j alone, as it is not a plugin type", (label) => {
        expect(splitPluginTypeLabel(label)).toBeUndefined();
    });
});
