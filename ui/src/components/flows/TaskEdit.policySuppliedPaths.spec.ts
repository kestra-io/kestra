import {describe, it, expect, vi} from "vitest"
import {h, inject} from "vue"
import {flushPromises} from "@vue/test-utils"
import KestraDesignSystem from "@kestra-io/design-system"
import {POLICY_SUPPLIED_PATHS_INJECTION_KEY} from "../no-code/injectionKeys"

const validateTask = vi.hoisted(() => vi.fn())

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}}),
    useRouter: () => ({replace: () => Promise.resolve(), push: () => Promise.resolve()}),
}))

vi.mock("../../stores/plugins", () => ({
    usePluginsStore: () => ({
        icons: {},
        plugin: undefined,
        editorPlugin: undefined,
        load: vi.fn(() => Promise.resolve()),
    }),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({
        user: {isAllowed: () => true},
    }),
}))

vi.mock("../../stores/flow", () => ({
    useFlowStore: () => ({
        flow: {namespace: "company.team"},
        flowParsed: {},
        taskError: undefined,
        validateTask,
    }),
}))

vi.mock("../../composables/playground/usePlaygroundRun", () => ({
    usePlaygroundRun: () => ({
        runTask: vi.fn(),
        playgroundStore: {enabled: false},
    }),
}))

vi.mock("../../composables/useContextSections", async () => {
    const {ref} = await import("vue")
    return {useContextSections: () => ({sections: ref([])})}
})

vi.mock("./TaskEditData.vue", () => ({
    default: {name: "TaskEditData", props: ["kind", "title", "subtitle", "sections", "filterable", "collapsible", "isCollapsed", "side"], template: "<div />"},
}))

const FakeFormReadingSuppliedPaths = {
    name: "TaskEditPanes",
    props: ["modelValue", "activeTab", "section", "readOnly", "pluginMarkdown", "editorPath"],
    setup() {
        const supplied = inject(POLICY_SUPPLIED_PATHS_INJECTION_KEY, undefined)
        return () => h("div", {"data-test": "supplied-paths"}, [...(supplied?.value ?? [])].join(","))
    },
}

import TaskEdit from "./TaskEdit.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

function mountTaskEdit() {
    return i18nMount(TaskEdit, {
        props: {
            task: {id: "log", type: "io.kestra.plugin.core.log.Log"},
            section: "tasks",
            flowId: "my_flow",
            namespace: "company.team",
            presentation: "panel",
        },
        global: {
            plugins: [KestraDesignSystem],
            stubs: {TaskEditPanes: FakeFormReadingSuppliedPaths},
        },
    })
}

describe("TaskEdit policy-supplied paths", () => {
    it("sends the flow namespace and provides the paths the backend reports", async () => {
        validateTask.mockResolvedValue({errors: [], policySuppliedPaths: ["message", "auth", "auth.user"]})

        const wrapper = mountTaskEdit()
        await flushPromises()

        expect(validateTask).toHaveBeenCalledWith(expect.objectContaining({namespace: "company.team", section: "tasks"}))
        expect(wrapper.find("[data-test='supplied-paths']").text()).toBe("message,auth,auth.user")
    })

    it("clears the paths when the response carries none", async () => {
        validateTask.mockResolvedValue({errors: []})

        const wrapper = mountTaskEdit()
        await flushPromises()

        expect(wrapper.find("[data-test='supplied-paths']").text()).toBe("")
    })
})
