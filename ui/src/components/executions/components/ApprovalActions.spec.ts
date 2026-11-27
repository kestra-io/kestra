import {beforeEach, describe, expect, it, vi} from "vitest"
import {computed, ref} from "vue"
import {flushPromises} from "@vue/test-utils"
import type {Execution} from "../../../stores/executions"

const reviewAllowed = vi.hoisted(() => ({value: true}))
vi.mock("../../../stores/executions", () => ({
    useExecutionsStore: () => ({
        flow: {tasks: [{id: "a", type: "io.kestra.plugin.core.flow.Approval"}]},
        isReviewAllowed: () => Promise.resolve(reviewAllowed.value),
    }),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => true}}),
}))

const approvalLink = ref<{label: string; to: unknown}>()
vi.mock("override/components/executions/overview/OverviewExtensions", () => ({
    useApprovalLink: () => computed(() => approvalLink.value),
}))

import ApprovalActions from "./ApprovalActions.vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const execution = {
    id: "e1",
    namespace: "ns",
    taskRunList: [{id: "tr-a", taskId: "a", state: {current: "PAUSED"}}],
} as unknown as Execution

const mountActions = () => i18nMount(ApprovalActions, {
    props: {execution, taskRun: {id: "tr-a"}},
    global: {
        stubs: {
            KsDropdown: {template: "<div v-bind=\"$attrs\" data-split><slot /><slot name=\"dropdown\" /></div>"},
            KsDropdownMenu: {template: "<div><slot /></div>"},
            KsDropdownItem: {template: "<a v-bind=\"$attrs\"><slot /></a>"},
            KsButton: {props: ["to", "tag", "icon"], template: "<button v-bind=\"$attrs\" :data-to=\"JSON.stringify(to)\"><slot /></button>"},
            ReviewDialog: {props: ["taskRun"], template: "<div data-test=\"dialog\" :data-task-run=\"taskRun.id\" />"},
        },
    },
})

describe("ApprovalActions", () => {
    beforeEach(() => {
        reviewAllowed.value = true
        approvalLink.value = undefined
    })

    it("offers the approval behind the request under the Review split button", async () => {
        approvalLink.value = {label: "Open approval", to: {name: "approvals/update", params: {id: "c1"}}}

        const wrapper = mountActions()
        await flushPromises()

        const review = wrapper.find("[data-test='approval-review']")
        expect(review.attributes()).toHaveProperty("data-split")
        expect(review.find("[data-test='open-approval']").text()).toBe("Open approval")

        await review.trigger("click")
        expect(wrapper.find("[data-test='dialog']").attributes("data-task-run")).toBe("tr-a")
    })

    it("shows a plain Review button when no approval backs the request", async () => {
        const wrapper = mountActions()
        await flushPromises()

        expect(wrapper.find("[data-split]").exists()).toBe(false)
        expect(wrapper.find("[data-test='approval-review']").exists()).toBe(true)
        expect(wrapper.find("[data-test='open-approval']").exists()).toBe(false)
    })

    it("still links the approval to a user who cannot review the request", async () => {
        reviewAllowed.value = false
        approvalLink.value = {label: "Open approval", to: {name: "approvals/update", params: {id: "c1"}}}

        const wrapper = mountActions()
        await flushPromises()

        expect(wrapper.find("[data-test='approval-review']").exists()).toBe(false)
        expect(JSON.parse(wrapper.find("[data-test='open-approval']").attributes("data-to")!)).toEqual({name: "approvals/update", params: {id: "c1"}})
    })
})
