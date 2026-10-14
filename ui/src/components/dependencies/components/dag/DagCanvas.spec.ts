import {beforeEach, describe, expect, it} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import DagCanvas from "./DagCanvas.vue"
import {DAG_CARD, DAG_CARD_WITH_TESTS_HEIGHT} from "../../utils/dagConstants"
import {ASSET, FLOW} from "../../utils/types"
import type {DbtTestFields, Element} from "../../utils/types"
import {i18nMount} from "../../../../../tests/unit/i18nMount"

const asset = (id: string, tests: DbtTestFields = {}): Element => ({
    data: {id, type: "NODE", flow: id, metadata: {subtype: ASSET, status: "fresh", ...tests}},
})

const flow = (id: string): Element => ({
    data: {id, type: "NODE", flow: id, namespace: "company.team", metadata: {subtype: FLOW}},
})

const cardHeightOf = (elements: Element[]) => {
    const wrapper = i18nMount(DagCanvas, {
        props: {elements},
        global: {stubs: {VueFlow: true, Background: true}},
    })
    return (wrapper.element as HTMLElement).style.getPropertyValue("--ks-dag-card-height")
}

describe("DagCanvas card height", () => {
    beforeEach(() => setActivePinia(createPinia()))

    it("keeps the compact card when no asset carries dbt tests", () => {
        expect(cardHeightOf([flow("producer"), asset("warehouse.raw_orders")])).toBe(`${DAG_CARD.height}px`)
    })

    it("grows every card by the footer row once one asset carries dbt tests", () => {
        expect(cardHeightOf([
            asset("warehouse.raw_orders"),
            asset("warehouse.stg_orders", {dbtTestStatus: "pass", dbtTestsTotal: 22, dbtTestsFailed: 0}),
        ])).toBe(`${DAG_CARD_WITH_TESTS_HEIGHT}px`)
    })
})
