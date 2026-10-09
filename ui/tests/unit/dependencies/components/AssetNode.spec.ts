import {describe, expect, it, beforeEach} from "vitest"
import {createPinia, setActivePinia} from "pinia"

import AssetNode from "../../../../src/components/dependencies/components/dag/AssetNode.vue"
import en from "../../../../src/translations/en.json"
import {i18nMount} from "../../i18nMount"

type Tests = {
    dbtTestStatus?: string;
    dbtTestsTotal?: number;
    dbtTestsFailed?: number;
};

function mountCard(tests: Tests = {}) {
    return i18nMount(AssetNode, {
        props: {
            id: "warehouse.dim_customers",
            data: {
                name: "dim_customers",
                status: "fresh",
                updated: "2026-09-24T08:00:00Z",
                ...tests,
            },
        },
        locales: en,
        global: {stubs: {Handle: true}},
    })
}

describe("AssetNode dbt test footer", () => {
    beforeEach(() => setActivePinia(createPinia()))

    it("renders no footer when the asset carries no dbt test keys", () => {
        expect(mountCard().find(".tests").exists()).toBe(false)
    })

    it("puts the failed count first and colours the row on fail", () => {
        const footer = mountCard({dbtTestStatus: "fail", dbtTestsTotal: 22, dbtTestsFailed: 2}).find(".tests")

        expect(footer.text()).toBe("2/22 failed")
        expect(footer.classes()).toContain("fail")
    })

    it("stays uncoloured on pass", () => {
        const footer = mountCard({dbtTestStatus: "pass", dbtTestsTotal: 22, dbtTestsFailed: 0}).find(".tests")

        expect(footer.text()).toBe("22 tests")
    })

    it("marks warn, which the counts alone cannot distinguish from a pass", () => {
        const footer = mountCard({dbtTestStatus: "warn", dbtTestsTotal: 22, dbtTestsFailed: 0}).find(".tests")

        expect(footer.text()).toBe("22 tests · warn")
        expect(footer.classes()).toContain("warn")
    })

    it("renders no footer for a status it does not recognise, rather than a pass it cannot vouch for", () => {
        const wrapper = mountCard({dbtTestStatus: "skipped", dbtTestsTotal: 22, dbtTestsFailed: 0})

        expect(wrapper.find(".tests").exists()).toBe(false)
    })

    it("pluralises a single test", () => {
        expect(mountCard({dbtTestStatus: "pass", dbtTestsTotal: 1, dbtTestsFailed: 0}).find(".tests").text())
            .toBe("1 test")
    })
})
