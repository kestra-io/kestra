import {beforeEach, describe, expect, it} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import MobileSummary from "./MobileSummary.vue"
import type {Execution} from "../../../../stores/executions"
import {i18nMount} from "../../../../../tests/unit/i18nMount"

const messages = {
    replayed: "Replayed",
    restarted: "Restarted",
    copy: "Copy",
    execution_mobile: {revision_short: "rev. {revision}"},
}

const execution = (labels: {key: string; value: string}[] = []) => ({
    id: "2qncB78egtgMA8D0HVACE2",
    namespace: "company.finance",
    flowId: "monthly_close",
    flowRevision: 2,
    labels,
    state: {current: "FAILED", histories: [{date: "2026-10-12T10:00:00Z", state: "CREATED"}]},
} as unknown as Execution)

const mountSummary = (labels?: {key: string; value: string}[]) => i18nMount(MobileSummary, {
    messages,
    props: {execution: execution(labels)},
    global: {
        stubs: {
            RouterLink: {template: "<a><slot /></a>"},
            ChangeExecutionStatus: true,
            SetLabels: true,
            Duration: true,
            KsTooltip: {template: "<div><slot /></div>"},
        },
    },
})

describe("MobileSummary", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    it("links to the flow and the namespace and shows the short revision", () => {
        // Given / When
        const wrapper = mountSummary()

        // Then
        const identity = wrapper.find(".mobile-summary-identity").text()
        expect(identity).toContain("monthly_close")
        expect(identity).toContain("company.finance")
        expect(wrapper.text()).toContain("rev. 2")
    })

    it("flags a replayed and a restarted execution", () => {
        // Given / When
        const wrapper = mountSummary([{key: "system.replayed", value: "true"}, {key: "system.restarted", value: "true"}])

        // Then
        expect(wrapper.text()).toContain("Replayed")
        expect(wrapper.text()).toContain("Restarted")
    })

    it("lists every label tag", () => {
        // Given / When
        const wrapper = mountSummary([{key: "team", value: "finance"}, {key: "env", value: "prod"}])

        // Then
        expect(wrapper.findAll(".labels-list .kel-tag")).toHaveLength(2)
    })
})
