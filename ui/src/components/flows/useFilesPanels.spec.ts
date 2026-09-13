import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent, h, inject, ref, type Ref} from "vue"

import {getTabFromFilesTab, useFilesPanels} from "./useFilesPanels"
import {FILES_CLOSE_TAB_INJECTION_KEY} from "../inputs/FileExplorer.vue"
import type {Panel} from "../../utils/multiPanelTypes"

vi.mock("override/stores/namespaces", () => ({
    useNamespacesStore: () => ({}),
}))
vi.mock("../../stores/flow", () => ({
    useFlowStore: () => ({haveChange: false}),
}))
vi.mock("../../composables/usePanelDefaultSize", () => ({
    usePanelDefaultSize: () => ({defaultSize: ref(50)}),
}))
vi.mock("../inputs/FlowFileEditorTab.vue", () => ({
    default: defineComponent({name: "FlowFileEditorTab", template: "<div />"}),
    FILES_REFRESH_CONTENT_INJECTION_KEY: Symbol("files-refresh-content"),
    FILES_SET_DIRTY_INJECTION_KEY: Symbol("files-set-dirty"),
    FILES_UPDATE_CONTENT_INJECTION_KEY: Symbol("files-update-content"),
}))

const tabFor = (path: string) => getTabFromFilesTab({
    name: path.split("/").pop()!,
    path,
    extension: path.split(".").pop()!,
    flow: false,
    dirty: false,
})

/**
 * `useFilesPanels` publishes its handlers with `provide`, so the close handler is captured the
 * way a descendant receives it: a parent runs the composable, a child injects the result.
 */
function mountWithTabs(paths: string[]) {
    const panels: Ref<Panel[]> = ref([])
    let closeTab!: (tab: {path: string}) => boolean

    const Child = defineComponent({
        setup() {
            closeTab = inject(FILES_CLOSE_TAB_INJECTION_KEY)!
            return () => null
        },
    })

    mount(defineComponent({
        setup() {
            useFilesPanels(panels, ref("io.kestra.test"))
            const tabs = paths.map(tabFor)
            panels.value = [{activeTab: tabs[0], tabs, size: 50}]
            return () => h(Child)
        },
    }))

    return {panels, closeTab}
}

const openPaths = (panels: Ref<Panel[]>) =>
    panels.value.flatMap(panel => panel.tabs.map(tab => tab.uid))

describe("useFilesPanels close handler", () => {
    /**
     * Tab order matters: the handler takes the first match, so a prefix test only picks the
     * wrong tab when the longer path sits earlier in the list. `a.py` / `a.python` collide
     * because "code-a.python" does start with "code-a.py".
     */
    it("should close the requested tab when a longer path listed before it shares its prefix", () => {
        const {panels, closeTab} = mountWithTabs(["a.python", "a.py"])

        const closed = closeTab({path: "a.py"})

        expect(closed).toBe(true)
        expect(openPaths(panels)).toEqual(["code-a.python"])
    })

    it("should close a nested path without touching a longer sibling listed before it", () => {
        const {panels, closeTab} = mountWithTabs(["src/a.python.py", "src/a.py"])

        closeTab({path: "src/a.py"})

        expect(openPaths(panels)).toEqual(["code-src/a.python.py"])
    })

    it("should report false when no tab is open for the path", () => {
        const {panels, closeTab} = mountWithTabs(["1.txt"])

        expect(closeTab({path: "other.txt"})).toBe(false)
        expect(openPaths(panels)).toEqual(["code-1.txt"])
    })

    it("should move the active tab to a surviving one after closing the active tab", () => {
        const {panels, closeTab} = mountWithTabs(["a.txt", "b.txt"])

        closeTab({path: "a.txt"})

        expect(panels.value[0].activeTab.uid).toBe("code-b.txt")
    })
})
