import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"

const findExecutions = vi.hoisted(() => vi.fn())
const user = vi.hoisted(() => ({hasAnyAction: vi.fn()}))

vi.mock("vue-router", () => ({useRouter: () => ({push: vi.fn()})}))
vi.mock("override/stores/auth", () => ({useAuthStore: () => ({user})}))
vi.mock("../../../../src/stores/executions", () => ({useExecutionsStore: () => ({findExecutions})}))

import PrevNext from "../../../../src/components/executions/overview/components/main/PrevNext.vue"
import {i18nMount} from "../../i18nMount"
import type {Execution} from "../../../../src/stores/executions"

function mountPrevNext() {
    return i18nMount(PrevNext, {
        props: {execution: {id: "e1", namespace: "ns", flowId: "flow", state: {startDate: "2026-10-01T10:00:00Z"}} as unknown as Execution},
        global: {stubs: {KsButton: {template: "<button><slot /></button>"}, KsIcon: true}},
    })
}

describe("PrevNext", () => {
    beforeEach(() => {
        findExecutions.mockReset().mockResolvedValue({results: []})
        user.hasAnyAction.mockReset()
    })

    it("searches the flow's other executions when the user can list executions", async () => {
        user.hasAnyAction.mockReturnValue(true)
        const wrapper = mountPrevNext()
        await flushPromises()

        expect(findExecutions).toHaveBeenCalledTimes(2)
        expect(wrapper.find("#buttons").exists()).toBe(true)
    })

    it("renders nothing and searches nothing when the user cannot list executions", async () => {
        user.hasAnyAction.mockReturnValue(false)
        const wrapper = mountPrevNext()
        await flushPromises()

        expect(findExecutions).not.toHaveBeenCalled()
        expect(wrapper.find("#buttons").exists()).toBe(false)
    })
})
