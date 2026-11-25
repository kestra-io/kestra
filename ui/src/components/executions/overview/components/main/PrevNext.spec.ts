import {describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"

const findExecutions = vi.hoisted(() => vi.fn())
const push = vi.hoisted(() => vi.fn())

vi.mock("vue-router", () => ({useRouter: () => ({push})}))
vi.mock("../../../../../stores/executions", () => ({useExecutionsStore: () => ({findExecutions})}))

import PrevNext from "./PrevNext.vue"
import type {Execution} from "../../../../../stores/executions"

const runs = ["e1", "e2", "e3"].map((id, i) => ({id, namespace: "ns", flowId: "flow", state: {startDate: `2026-10-01T10:0${i}:00Z`}}))

describe("PrevNext", () => {
    it("opens the adjacent newer execution on previous, not the newest one", async () => {
        findExecutions.mockImplementation(async (params: Record<string, string | number>) => {
            const after = params["filters[startDate][GREATER_THAN]"]
            const before = params["filters[startDate][LESS_THAN]"]
            const sorted = runs
                .filter((run) => (!after || run.state.startDate > after) && (!before || run.state.startDate < before))
                .sort((a, b) => a.state.startDate.localeCompare(b.state.startDate))
            if (params.sort === "state.startDate:desc") sorted.reverse()
            return {results: sorted.slice(0, Number(params.size))}
        })
        const wrapper = mount(PrevNext, {
            props: {execution: runs[0] as unknown as Execution},
            global: {
                plugins: [createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false, messages: {en: {}}})],
                stubs: {KsButton: {template: "<button><slot /></button>"}, KsIcon: true},
            },
        })
        await flushPromises()

        await wrapper.findAll("button")[0].trigger("click")

        expect(push).toHaveBeenCalledWith(expect.objectContaining({params: expect.objectContaining({id: "e2"})}))
    })
})
