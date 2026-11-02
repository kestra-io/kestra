import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"

const route = vi.hoisted(() => ({query: {} as Record<string, string>}))
const replace = vi.hoisted(() => vi.fn())
const isReviewAllowed = vi.hoisted(() => vi.fn())
vi.mock("vue-router", () => ({
    useRoute: () => route,
    useRouter: () => ({replace}),
}))

vi.mock("../../stores/executions", () => ({
    useExecutionsStore: () => ({
        flow: {id: "f1", namespace: "ns", tasks: [{id: "approval", type: "io.kestra.plugin.core.flow.Approval"}]},
        isReviewAllowed,
    }),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => true}}),
}))

import ReviewLink from "./ReviewLink.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import type {Execution} from "../../stores/executions"

function mountReview(state: string, otherState = "SUCCESS") {
    return i18nMount(ReviewLink, {
        messages: {approval: {review: "Review"}},
        props: {
            execution: {id: "e1", namespace: "ns", flowId: "f1", taskRunList: [
                {id: "tr0", taskId: "approval", state: {current: otherState}},
                {id: "tr1", taskId: "approval", state: {current: state}},
            ]} as unknown as Execution,
        },
        global: {
            stubs: {
                ReviewDialog: {props: ["modelValue"], template: "<div data-test=\"dialog\" :data-open=\"modelValue\" />"},
                ReviewedDialog: {props: ["modelValue"], template: "<div data-test=\"reviewed\" :data-open=\"modelValue\" />"},
            },
        },
    })
}

describe("ReviewLink", () => {
    beforeEach(() => {
        route.query = {}
        replace.mockReset()
        isReviewAllowed.mockReset().mockResolvedValue(false)
    })

    it("shows the review that was made for a review link to an approval that is already decided", async () => {
        route.query = {review: "tr1"}
        const wrapper = mountReview("SUCCESS")
        await flushPromises()

        expect(wrapper.find("[data-test='dialog']").exists()).toBe(false)
        expect(wrapper.find("[data-test='reviewed']").attributes("data-open")).toBe("true")
        expect(replace).toHaveBeenCalledWith({query: {}})
    })

    it("does not open the dialog for a paused approval the viewer may not review", async () => {
        route.query = {review: "tr1"}
        const wrapper = mountReview("PAUSED")
        await flushPromises()

        expect(wrapper.find("[data-test='dialog']").exists()).toBe(false)
        expect(replace).toHaveBeenCalledWith({query: {}})
    })

    it("drops a review link to a task run the execution does not have", async () => {
        route.query = {review: "unknown", tab: "gantt"}
        const wrapper = mountReview("PAUSED")
        await flushPromises()

        expect(wrapper.find("[data-test='dialog']").exists()).toBe(false)
        expect(replace).toHaveBeenCalledWith({query: {tab: "gantt"}})
    })

    it("waits for an approval that is still pausing instead of calling it decided", async () => {
        route.query = {review: "tr1"}
        const wrapper = mountReview("PAUSING")
        await flushPromises()

        expect(wrapper.find("[data-test='reviewed']").exists()).toBe(false)
        expect(wrapper.find("[data-test='dialog']").exists()).toBe(false)
        expect(replace).not.toHaveBeenCalled()
    })

    it("checks the permission on the linked approval, not on the first paused one", async () => {
        route.query = {review: "tr1"}
        isReviewAllowed.mockImplementation(({taskRunId}: {taskRunId: string}) => Promise.resolve(taskRunId === "tr1"))
        const wrapper = mountReview("PAUSED", "PAUSED")
        await flushPromises()

        expect(isReviewAllowed).toHaveBeenCalledWith({id: "e1", taskRunId: "tr1"})
        expect(wrapper.find("[data-test='dialog']").attributes("data-open")).toBe("true")
    })

    it("opens the dialog for a paused approval the viewer may review", async () => {
        route.query = {review: "tr1"}
        isReviewAllowed.mockResolvedValue(true)
        const wrapper = mountReview("PAUSED")
        await flushPromises()

        expect(wrapper.find("[data-test='dialog']").attributes("data-open")).toBe("true")
    })
})
