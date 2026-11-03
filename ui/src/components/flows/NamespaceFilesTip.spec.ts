import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {ref} from "vue"
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
import {KsAlert} from "@kestra-io/design-system"
import {i18nMount} from "../../../tests/unit/i18nMount"
import NamespaceFilesTip from "./NamespaceFilesTip.vue"
import {OPEN_EDITOR_TAB_INJECTION_KEY} from "../no-code/injectionKeys"

const INLINE_PYTHON_YAML = `
id: inline
namespace: company.team
tasks:
  - id: python
    type: io.kestra.plugin.scripts.python.Script
    script: |
      print("hello")
`.trim()

const DISMISSED_KEY = "namespaceFilesTipDismissed"
const CLOSED_KEY = "namespaceFilesTipClosed"

const TIP = "[data-test='namespace-files-tip']"
const OPEN_FILES = "[data-test='namespace-files-tip-open-files']"
const DISMISS = "[data-test='namespace-files-tip-dismiss']"

const mockFlowParsed = ref<unknown>(undefined)

vi.mock("../../stores/flow", () => ({
    useFlowStore: () => ({
        get flowParsed() { return mockFlowParsed.value },
    }),
}))

function mountTip(openEditorTab: (uid: string) => void) {
    return i18nMount(NamespaceFilesTip, {
        global: {
            provide: {[OPEN_EDITOR_TAB_INJECTION_KEY]: openEditorTab},
        },
    })
}

describe("NamespaceFilesTip", () => {
    beforeEach(() => {
        localStorage.clear()
        sessionStorage.clear()
        mockFlowParsed.value = YAML_UTILS.parse(INLINE_PYTHON_YAML)
    })

    afterEach(() => sessionStorage.clear())

    it("renders when the flow has a Python Script task with an inline script", () => {
        const wrapper = mountTip(vi.fn())

        expect(wrapper.find(TIP).exists()).toBe(true)
    })

    it("opens the files tab when clicking \"Open Files\"", async () => {
        const openEditorTab = vi.fn()
        const wrapper = mountTip(openEditorTab)

        await wrapper.find(OPEN_FILES).trigger("click")

        expect(openEditorTab).toHaveBeenCalledWith("files")
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

    it("hides the tip for the session only when clicking the close icon", async () => {
        const wrapper = mountTip(vi.fn())

        await wrapper.findComponent(KsAlert).vm.$emit("close")

        expect(wrapper.find(TIP).exists()).toBe(false)
        expect(sessionStorage.getItem(CLOSED_KEY)).toBe("true")
        expect(localStorage.getItem(DISMISSED_KEY)).toBeNull()
    })

    it("stays hidden after remount when closed earlier in the session", () => {
        sessionStorage.setItem(CLOSED_KEY, "true")

        const wrapper = mountTip(vi.fn())

        expect(wrapper.find(TIP).exists()).toBe(false)
    })
})
