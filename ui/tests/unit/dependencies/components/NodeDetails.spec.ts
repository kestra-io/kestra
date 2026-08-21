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

describe("dependencies NodeDetails.vue", () => {
    // P1. The panel shows the trailing segment, matching the canvas card; the full FQCN would
    // push every other value off a narrow pane.
    it("shows the asset type as its trailing segment, not the FQCN", () => {
        const rows = rowsOf(mountDetails({
            id: "proj.raw.raw_orders",
            type: "NODE",
            flow: "proj.raw.raw_orders",
            namespace: "dbt.demo",
            metadata: {subtype: ASSET, assetType: "io.kestra.plugin.ee.assets.Table"},
        } as Node))

        expect(rows["Type"]).toBe("Table")
    })

    // P0. Dependencies.vue mounts this panel for all four views (flow, execution, namespace,
    // asset). Rows added for assets must stay invisible in the other three rather than render
    // blank, and regressions in those three have happened repeatedly on this branch.
    it("renders no asset rows for a flow node", () => {
        const rows = rowsOf(mountDetails({
            id: "dbt.demo_multi_plugin_assets",
            type: "NODE",
            flow: "multi_plugin_assets",
            namespace: "dbt.demo",
            metadata: {subtype: FLOW},
        } as Node, FLOW))

        expect(rows).not.toHaveProperty("Type")
        expect(rows).not.toHaveProperty("Plugins")
        expect(rows).not.toHaveProperty("System")
        expect(rows["Namespace"]).toBe("dbt.demo")
    })
})
