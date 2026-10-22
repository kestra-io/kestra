import {describe, expect, it} from "vitest"
import {RouterLink, createMemoryHistory, createRouter} from "vue-router"

import ParentExecutionLink from "./ParentExecutionLink.vue"
import type {Execution} from "../../../../stores/executions"
import {i18nMount} from "../../../../../tests/unit/i18nMount"

const LINK = "[data-test=\"execution-parent-link\"]"
const ORIGINAL = "[data-test=\"execution-original-link\"]"

const execution = (overrides: Record<string, unknown> = {}) =>
    ({
        id: "child-execution",
        namespace: "child-namespace",
        flowId: "child-flow",
        ...overrides,
    }) as unknown as Execution

const startedBy = (type: string, variables: Record<string, unknown>) =>
    execution({trigger: {id: "upstream", type, variables}})

const lineage = (executionId: string) => ({
    executionId,
    namespace: "parent-namespace",
    flowId: "parent-flow",
})

async function mountLink(value: Execution) {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [
            {name: "current", path: "/current", component: {template: "<div />"}},
            {
                name: "executions/update",
                path: "/executions/:namespace/:flowId/:id/:tab?",
                component: {template: "<div />"},
            },
        ],
    })
    await router.push({name: "current"})
    await router.isReady()

    return i18nMount(ParentExecutionLink, {
        props: {execution: value},
        global: {plugins: [router]},
        messages: {
            "original execution": "Original execution",
            "parent execution": "Parent execution",
        },
    })
}

describe("ParentExecutionLink", () => {
    it("shouldRenderTheOriginalExecutionRowItOwns", async () => {
        const wrapper = await mountLink(execution({originalId: "first-execution"}))
        const original = wrapper.find(ORIGINAL)

        expect(original.exists()).toBe(true)
        expect(original.text()).toContain("Original execution: first-execution")
    })

    it("shouldLinkToTheUpstreamExecutionInItsOwnFlowForASubflowChild", async () => {
        const wrapper = await mountLink(
            startedBy("io.kestra.plugin.core.flow.Subflow", lineage("parent-execution")),
        )
        const parent = wrapper.find(LINK)

        expect(parent.exists()).toBe(true)
        expect(parent.text()).toContain("Parent execution: parent-execution")
        expect(wrapper.findComponent(RouterLink).props("to")).toEqual({
            name: "executions/update",
            params: {
                tab: "overview",
                id: "parent-execution",
                namespace: "parent-namespace",
                flowId: "parent-flow",
            },
        })
    })

    it("shouldLinkToTheUpstreamExecutionOfAFlowTriggerChild", async () => {
        const wrapper = await mountLink(
            startedBy("io.kestra.plugin.core.trigger.Flow", lineage("upstream-execution")),
        )

        expect(wrapper.find(LINK).text()).toContain("Parent execution: upstream-execution")
        expect(wrapper.findComponent(RouterLink).props("to")).toEqual({
            name: "executions/update",
            params: {
                tab: "overview",
                id: "upstream-execution",
                namespace: "parent-namespace",
                flowId: "parent-flow",
            },
        })
    })

    it("shouldKeepTheCurrentFlowWhenTheParentComesFromParentId", async () => {
        const wrapper = await mountLink(execution({parentId: "restarted-from"}))

        expect(wrapper.find(LINK).text()).toContain("Parent execution: restarted-from")
        expect(wrapper.findComponent(RouterLink).props("to")).toEqual({
            name: "executions/update",
            params: {
                tab: "overview",
                id: "restarted-from",
                namespace: "child-namespace",
                flowId: "child-flow",
            },
        })
    })

    it("shouldCarryTheTenantWhenTheExecutionHasOne", async () => {
        const wrapper = await mountLink(
            execution({tenantId: "acme", parentId: "restarted-from"}),
        )

        expect(wrapper.findComponent(RouterLink).props("to")).toEqual({
            name: "executions/update",
            params: {
                tab: "overview",
                tenant: "acme",
                id: "restarted-from",
                namespace: "child-namespace",
                flowId: "child-flow",
            },
        })
    })

    it("shouldIgnoreATriggerThatExposesAnExecutionIdWithoutItsFlow", async () => {
        const wrapper = await mountLink(
            startedBy("io.kestra.plugin.core.trigger.Webhook", {
                executionId: "unrelated-execution",
            }),
        )

        expect(wrapper.find(LINK).exists()).toBe(false)
    })

    it("shouldFallBackToParentIdWhenTheTriggerIsNotLineage", async () => {
        const wrapper = await mountLink(
            execution({
                parentId: "restarted-from",
                trigger: {
                    id: "unrelated",
                    type: "io.kestra.plugin.core.trigger.Webhook",
                    variables: {executionId: "unrelated-execution"},
                },
            }),
        )

        expect(wrapper.find(LINK).text()).toContain("Parent execution: restarted-from")
    })

    it("shouldHideWhenExecutionHasNoParent", async () => {
        const wrapper = await mountLink(execution())

        expect(wrapper.find(LINK).exists()).toBe(false)
    })

    it("shouldHideWhenTheParentIsAlreadyShownAsTheOriginalExecution", async () => {
        const wrapper = await mountLink(
            execution({parentId: "first-execution", originalId: "first-execution"}),
        )

        expect(wrapper.find(LINK).exists()).toBe(false)
    })
})
