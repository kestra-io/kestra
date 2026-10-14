import {describe, expect, it} from "vitest"

import DagToolbar from "../../../../src/components/dependencies/components/dag/DagToolbar.vue"
import {ASSET} from "../../../../src/components/dependencies/utils/types"
import en from "../../../../src/translations/en.json"
import {i18nMount} from "../../i18nMount"

type Tests = {
    dbtTestStatus?: string;
    dbtTestsTotal?: number;
    dbtTestsFailed?: number;
};

const asset = (id: string, tests: Tests = {}) => ({
    id,
    type: "NODE" as const,
    flow: id,
    metadata: {
        subtype: ASSET,
        status: "fresh",
        updated: new Date().toISOString(),
        ...tests,
    },
})

function mountToolbar(nodes: ReturnType<typeof asset>[]) {
    return i18nMount(DagToolbar, {
        props: {nodes, groupFields: [], groupChips: [], layoutMode: "dag" as const, groupField: ""},
        locales: en,
        global: {stubs: {GroupPicker: true}},
    })
}

describe("DagToolbar summary", () => {
    it("reports assets with failing tests instead of calling the graph all fresh", () => {
        const summary = mountToolbar([
            asset("warehouse.staging.stg_orders", {dbtTestStatus: "pass", dbtTestsTotal: 22, dbtTestsFailed: 0}),
            asset("warehouse.marts.fct_orders", {dbtTestStatus: "fail", dbtTestsTotal: 22, dbtTestsFailed: 2}),
        ]).find(".summary")

        expect(summary.text()).toContain("1 with failing tests")
        expect(summary.text()).not.toContain("fresh")
    })

    it("still calls the graph all fresh when no asset has failing tests", () => {
        const summary = mountToolbar([
            asset("warehouse.raw.raw_orders"),
            asset("warehouse.staging.stg_orders", {dbtTestStatus: "pass", dbtTestsTotal: 22, dbtTestsFailed: 0}),
        ]).find(".summary")

        expect(summary.text()).toContain("All 2 assets fresh")
    })
})
