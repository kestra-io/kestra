import {describe, it, expect, vi, beforeEach} from "vitest"
import {ref} from "vue"
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
import {i18nMount} from "../../i18nMount"
import NamespaceFilesTip from "../../../../src/components/flows/NamespaceFilesTip.vue"
import {OPEN_EDITOR_TAB_INJECTION_KEY} from "../../../../src/components/no-code/injectionKeys"

const INLINE_PYTHON_YAML = `
id: inline
namespace: company.team
tasks:
  - id: python
    type: io.kestra.plugin.scripts.python.Script
    script: |
      print("hello")
`.trim()

const NO_PYTHON_YAML = `
id: log
namespace: company.team
tasks:
  - id: log
    type: io.kestra.plugin.core.log.Log
    message: hello
`.trim()

const NAMESPACE_FILES_YAML = `
id: ns-files
namespace: company.team
tasks:
  - id: python
    type: io.kestra.plugin.scripts.python.Script
    namespaceFiles:
      enabled: true
    script: print("hello")
`.trim()

const DISMISSED_KEY = "namespaceFilesTipDismissed"

const TIP = "[data-test='namespace-files-tip']"
const OPEN_FILES = "[data-test='namespace-files-tip-open-files']"
const DISMISS = "[data-test='namespace-files-tip-dismiss']"
const CLOSE = "[data-test='namespace-files-tip-close']"

const mockFlowParsed = ref<unknown>(undefined)

vi.mock("../../../../src/stores/flow", () => ({
    useFlowStore: () => ({
        get flowParsed() { return mockFlowParsed.value },
    }),
}))

function mountTip(openEditorTab?: (uid: string) => void) {
    return i18nMount(NamespaceFilesTip, {
        global: {
            provide: openEditorTab ? {[OPEN_EDITOR_TAB_INJECTION_KEY]: openEditorTab} : {},
        },
    })
}

describe("NamespaceFilesTip", () => {
    beforeEach(() => {
        localStorage.clear()
        mockFlowParsed.value = YAML_UTILS.parse(INLINE_PYTHON_YAML)
    })

    it("renders when the flow has a Python Script task with an inline script", () => {
        const wrapper = mountTip(vi.fn())

        expect(wrapper.find(TIP).exists()).toBe(true)
    })

    it("does not render when the flow has no Python Script task", () => {
        mockFlowParsed.value = YAML_UTILS.parse(NO_PYTHON_YAML)

        const wrapper = mountTip(vi.fn())

        expect(wrapper.find(TIP).exists()).toBe(false)
    })

    it("does not render when the task already uses Namespace Files", () => {
        mockFlowParsed.value = YAML_UTILS.parse(NAMESPACE_FILES_YAML)

        const wrapper = mountTip(vi.fn())

        expect(wrapper.find(TIP).exists()).toBe(false)
    })

    it("opens the files tab when clicking \"Open Files\"", async () => {
        const openEditorTab = vi.fn()
        const wrapper = mountTip(openEditorTab)

        await wrapper.find(OPEN_FILES).trigger("click")

        expect(openEditorTab).toHaveBeenCalledWith("files")
    })

    it("does not render \"Open Files\" when no editor tab opener is provided", () => {
        const wrapper = mountTip()

        expect(wrapper.find(TIP).exists()).toBe(true)
        expect(wrapper.find(OPEN_FILES).exists()).toBe(false)
    })

    it("hides the tip and remembers the choice when clicking \"Don't show again\"", async () => {
        const wrapper = mountTip(vi.fn())

        await wrapper.find(DISMISS).trigger("click")

        expect(wrapper.find(TIP).exists()).toBe(false)
        expect(localStorage.getItem(DISMISSED_KEY)).toBe("true")
    })

    it("does not render when the tip was dismissed before", () => {
        localStorage.setItem(DISMISSED_KEY, "true")

        const wrapper = mountTip(vi.fn())

        expect(wrapper.find(TIP).exists()).toBe(false)
    })

    it("hides the tip without remembering it when clicking the close icon", async () => {
        const wrapper = mountTip(vi.fn())

        await wrapper.find(CLOSE).trigger("click")

        expect(wrapper.find(TIP).exists()).toBe(false)
        expect(localStorage.getItem(DISMISSED_KEY)).toBeNull()
    })
})
