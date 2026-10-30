import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent, h, inject, ref, type Ref} from "vue"

import {getTabFromFilesTab, useFilesPanels} from "./useFilesPanels"
import {FILES_CLOSE_TAB_INJECTION_KEY} from "../inputs/FileExplorer.vue"
import {FILES_REFRESH_CONTENT_INJECTION_KEY, FILES_UPDATE_CONTENT_INJECTION_KEY} from "../inputs/FlowFileEditorTab.vue"
import type {Panel, TabLive} from "../../utils/multiPanelTypes"

const saveOrCreateFile = vi.fn()
const flowStore: {haveChange: boolean; filesSaveAll?: (() => Promise<void>) | null} = {haveChange: false}

vi.mock("override/stores/namespaces", () => ({
    useNamespacesStore: () => ({saveOrCreateFile}),
}))
vi.mock("../../stores/flow", () => ({
    useFlowStore: () => flowStore,
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
    let updateContent!: (payload: {path: string; content: string}) => void
    let refreshedContents!: Ref<Record<string, {content: string}>>

    const Child = defineComponent({
        setup() {
            closeTab = inject(FILES_CLOSE_TAB_INJECTION_KEY)!
            updateContent = inject(FILES_UPDATE_CONTENT_INJECTION_KEY)!
            refreshedContents = inject(FILES_REFRESH_CONTENT_INJECTION_KEY)!
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

    return {panels, closeTab, updateContent, refreshedContents}
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

    it("should keep the active tab when a background tab is closed", () => {
        const {panels, closeTab} = mountWithTabs(["a.txt", "b.txt", "c.txt"])
        // a.txt is active by default; close background tab c.txt.

        closeTab({path: "c.txt"})

        expect(panels.value[0].activeTab.uid).toBe("code-a.txt")
        expect(openPaths(panels)).toEqual(["code-a.txt", "code-b.txt"])
    })

})

describe("useFilesPanels save all", () => {
    beforeEach(() => {
        saveOrCreateFile.mockReset()
    })

    function editFile(path: string, content: string) {
        const mounted = mountWithTabs([path])
        mounted.updateContent({path, content})
        const tab = mounted.panels.value[0].tabs[0] as TabLive
        tab.dirty = true
        return mounted
    }

    it("should give the editor the saved content as its new baseline", async () => {
        saveOrCreateFile.mockResolvedValue(undefined)
        const {panels, refreshedContents} = editFile("data.txt", "edited")

        await flowStore.filesSaveAll!()

        expect(saveOrCreateFile).toHaveBeenCalledWith({namespace: "io.kestra.test", path: "data.txt", content: "edited"})
        expect((panels.value[0].tabs[0] as TabLive).dirty).toBe(false)
        expect(refreshedContents.value["data.txt"]).toEqual({content: "edited"})
    })

    it("should keep a file dirty when it was edited while it was being saved", async () => {
        let finishSave: () => void = () => {}
        saveOrCreateFile.mockReturnValue(new Promise<void>((resolve) => finishSave = resolve))
        const {panels, updateContent, refreshedContents} = editFile("data.txt", "edited")

        const saving = flowStore.filesSaveAll!()
        updateContent({path: "data.txt", content: "edited again"})
        finishSave()
        await saving

        expect((panels.value[0].tabs[0] as TabLive).dirty).toBe(true)
        expect(refreshedContents.value["data.txt"]).toBeUndefined()
    })
})
