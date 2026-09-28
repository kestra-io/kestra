import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {RouterLink, createMemoryHistory, createRouter} from "vue-router"

import ParentExecutionLink from "./ParentExecutionLink.vue"
import type {Execution} from "../../../../stores/executions"

const LINK = "[data-test=\"execution-parent-link\"]"

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
                path: "/executions/:namespace/:flowId/:id/:tab?",
                component: {template: "<div />"},
            },
        ],
    })
    await router.push({name: "current"})
    await router.isReady()

    return mount(ParentExecutionLink, {
        props: {execution: value},
        global: {
            plugins: [
                router,
                createI18n({
                    legacy: false,
                    locale: "en",
                    messages: {en: {"parent execution": "Parent execution"}},
                }),
            ],
        },
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
        expect(wrapper.findComponent(RouterLink).props("to")).toEqual({
            name: "executions/update",
            params: {
                tab: "overview",
                id: "parent-execution",
                namespace: "child-namespace",
                flowId: "child-flow",
            },
        })
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
