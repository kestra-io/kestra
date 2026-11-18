import {describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"

vi.mock("../../../../../stores/executions", () => ({
    useExecutionsStore: () => ({
        flow: {tasks: [{id: "a", type: "io.kestra.plugin.core.flow.Approval"}, {id: "b", type: "io.kestra.plugin.core.flow.Approval"}]},
        isReviewAllowed: () => Promise.resolve(true),
    }),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => true}}),
}))

import Review from "./Review.vue"
import {i18nMount} from "../../../../../../tests/unit/i18nMount"
import type {Execution} from "../../../../../stores/executions"

function execution(aState: string): Execution {
    return {
        id: "e1",
        namespace: "ns",
        taskRunList: [
            {id: "tr-a", taskId: "a", state: {current: aState}},
            {id: "tr-b", taskId: "b", state: {current: "PAUSED"}},
        ],
    } as unknown as Execution
}

describe("Review", () => {
    it("keeps the dialog on the approval it was opened for once another reviewer decides it", async () => {
        const wrapper = i18nMount(Review, {
            props: {execution: execution("PAUSED")},
            global: {
                stubs: {
                    NavBarAction: {template: "<button v-bind=\"$attrs\"><slot /></button>"},
                    ReviewDialog: {props: ["taskRun"], template: "<div data-test=\"dialog\" :data-task-run=\"taskRun.id\" />"},
                },
            },
        })
        await flushPromises()

        await wrapper.find("[data-test='review-action']").trigger("click")
        await wrapper.setProps({execution: execution("SUCCESS")})
        await flushPromises()

        expect(wrapper.find("[data-test='dialog']").attributes("data-task-run")).toBe("tr-a")
    })
})
