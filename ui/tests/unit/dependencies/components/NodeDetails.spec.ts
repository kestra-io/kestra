import {describe, it, expect} from "vitest"
import {createI18n} from "vue-i18n"
import {mount, RouterLinkStub} from "@vue/test-utils"
import KestraDesignSystem from "@kestra-io/design-system"

import NodeDetails from "../../../../src/components/dependencies/components/NodeDetails.vue"
import {ASSET, FLOW} from "../../../../src/components/dependencies/utils/types"
import type {Node} from "../../../../src/components/dependencies/utils/types"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
            back: "Back",
            type: "Type",
            namespace: "Namespace",
            flows: "Flows",
            plugins: {names: "Plugins"},
            dependency: {dag: {
                last_update: "Last update",
                system: "System",
                recent_runs: "Recent runs",
                status: {fresh: "Fresh", stale: "Stale"},
            }},
        },
    },
})

const mountDetails = (node: Node, subtype = ASSET) => mount(NodeDetails, {
    props: {node, subtype},
    global: {
        plugins: [i18n, KestraDesignSystem],
        stubs: {RouterLink: RouterLinkStub, Link: true},
    },
})

/** Label/value pairs as rendered, so a test reads the way the panel does. */
const rowsOf = (wrapper: ReturnType<typeof mountDetails>): Record<string, string> => {
    const labels = wrapper.findAll(".details-label").map((el) => el.text())
    const values = wrapper.findAll(".details-value").map((el) => el.text())
    return Object.fromEntries(labels.map((label, index) => [label, values[index]]))
}

const assetNode = (metadata: Record<string, unknown> = {}): Node => ({
    id: "proj.raw.raw_orders",
    type: "NODE",
    flow: "proj.raw.raw_orders",
    namespace: "dbt.demo",
    metadata: {subtype: ASSET, ...metadata},
} as Node)

describe("dependencies NodeDetails.vue — asset metadata rows", () => {
    it("shows the asset type as its trailing segment, not the FQCN", () => {
        const rows = rowsOf(mountDetails(assetNode({assetType: "io.kestra.plugin.ee.assets.Table"})))

        expect(rows["Type"]).toBe("Table")
    })

    // The producer keeps its full FQCN here: "which task wrote this" is the question the
    // panel is open for, and the trailing segment alone would not answer it.
    it("shows the producing task type in full", () => {
        const rows = rowsOf(mountDetails(assetNode({producer: "io.kestra.plugin.jdbc.duckdb.Query"})))

        expect(rows["Plugins"]).toBe("io.kestra.plugin.jdbc.duckdb.Query")
    })

    it("omits both rows when the asset carries neither value", () => {
        const rows = rowsOf(mountDetails(assetNode()))

        expect(rows).not.toHaveProperty("Type")
        expect(rows).not.toHaveProperty("Plugins")
    })
})

// Dependencies.vue mounts NodeDetails for all four views, so a row added for assets must
// stay invisible in the flow, execution and namespace graphs rather than render blank.
describe("dependencies NodeDetails.vue — non-asset views", () => {
    it("renders no asset rows for a flow node", () => {
        const flowNode = {
            id: "dbt.demo_multi_plugin_assets",
            type: "NODE",
            flow: "multi_plugin_assets",
            namespace: "dbt.demo",
            metadata: {subtype: FLOW},
        } as Node

        const rows = rowsOf(mountDetails(flowNode, FLOW))

        expect(rows).not.toHaveProperty("Type")
        expect(rows).not.toHaveProperty("Plugins")
        expect(rows).not.toHaveProperty("System")
        expect(rows["Namespace"]).toBe("dbt.demo")
    })
})
