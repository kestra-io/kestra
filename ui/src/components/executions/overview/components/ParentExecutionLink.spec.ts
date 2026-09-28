import {describe, expect, it} from "vitest"
import {createMemoryHistory, createRouter} from "vue-router"

import {i18nMount} from "../../../../../tests/unit/i18nMount"
import ParentExecutionLink from "./ParentExecutionLink.vue"
import type {Execution} from "../../../../stores/executions"

const LINK = "[data-test=\'execution-parent-link\']"

const execution = (overrides: Record<string, unknown> = {}) =>
    ({
        id: "child-execution",
        namespace: "child-namespace",
        flowId: "child-flow",
        ...overrides,
    }) as unknown as Execution

const startedBy = (type: string, executionId: string) =>
    execution({trigger: {id: "upstream", type, variables: {executionId}}})

async function mountLink(value: Execution) {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [
            {name: "current", path: "/current", component: {template: "<div />"}},
            {
                name: "executions/update",
                path: "/:tenant?/executions/:namespace/:flowId/:id",
                component: {template: "<div />"},
            },
        ],
    })
    await router.push({name: "current"})
    await router.isReady()

    return i18nMount(ParentExecutionLink, {
        props: {execution: value},
        messages: {"parent execution": "Parent execution"},
        global: {plugins: [router]},
    })
}

describe("ParentExecutionLink", () => {
    it("shouldLinkToTheUpstreamExecutionOfASubflowChild", async () => {
        const wrapper = await mountLink(
            startedBy("io.kestra.plugin.core.flow.Subflow", "parent-execution"),
        )
        const parent = wrapper.find(LINK)

        expect(parent.exists()).toBe(true)
        expect(parent.text()).toContain("Parent execution: parent-execution")
        expect(parent.attributes("href")).toContain("/child-namespace/child-flow/parent-execution")
    })

    it("shouldLinkToTheUpstreamExecutionOfAFlowTriggerChild", async () => {
        const wrapper = await mountLink(
            startedBy("io.kestra.plugin.core.trigger.Flow", "upstream-execution"),
        )

        expect(wrapper.find(LINK).text()).toContain("Parent execution: upstream-execution")
    })

    it("shouldFallBackToParentIdForRestartLineage", async () => {
        const wrapper = await mountLink(execution({parentId: "restarted-from"}))

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
