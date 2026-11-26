import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"

const findExecutions = vi.hoisted(() => vi.fn())
const push = vi.hoisted(() => vi.fn())

vi.mock("vue-router", () => ({useRouter: () => ({push})}))
vi.mock("../../../../../stores/executions", () => ({useExecutionsStore: () => ({findExecutions})}))

import PrevNext from "./PrevNext.vue"
import type {Execution} from "../../../../../stores/executions"

const runs = ["e1", "e2", "e3"].map((id, i) => ({id, namespace: "ns", flowId: "flow", state: {startDate: `2026-10-01T10:0${i}:00Z`}}))
const childNamespaceRun = {id: "sub", namespace: "ns.sub", flowId: "flow", state: {startDate: "2026-10-01T10:05:00Z"}}

async function searchRuns(params: Record<string, string | number>) {
    const after = params["filters[startDate][GREATER_THAN]"]
    const before = params["filters[startDate][LESS_THAN]"]
    const prefix = params["filters[namespace][PREFIX]"]
    const sorted = [...runs, childNamespaceRun]
        .filter((run) => prefix ? run.namespace.startsWith(String(prefix)) : run.namespace === params["filters[namespace][EQUALS]"])
        .filter((run) => (!after || run.state.startDate > after) && (!before || run.state.startDate < before))
        .sort((a, b) => a.state.startDate.localeCompare(b.state.startDate))
    if (params.sort === "state.startDate:desc") sorted.reverse()
    return {results: sorted.slice(0, Number(params.size))}
}

function mountPrevNext(execution = runs[0]) {
    return mount(PrevNext, {
        props: {execution: execution as unknown as Execution},
        global: {
            plugins: [createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false, messages: {en: {}}})],
            stubs: {KsButton: {template: "<button><slot /></button>"}, KsIcon: true},
        },
    })
}

describe("PrevNext", () => {
    beforeEach(() => {
        findExecutions.mockReset().mockImplementation(searchRuns)
        push.mockReset()
    })

    it("opens the adjacent newer execution on previous, not the newest one", async () => {
        const wrapper = mountPrevNext()
        await flushPromises()

        await wrapper.findAll("button")[0].trigger("click")

        expect(push).toHaveBeenCalledWith(expect.objectContaining({params: expect.objectContaining({id: "e2"})}))
    })

    it("does not open a run of the same flow id in a child namespace", async () => {
        const wrapper = mountPrevNext(runs[2])
        await flushPromises()

        await wrapper.findAll("button")[0].trigger("click")

        expect(push).not.toHaveBeenCalled()
    })
})
