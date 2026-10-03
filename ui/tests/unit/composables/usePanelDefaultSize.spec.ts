import {describe, expect, it} from "vitest"
import {ref} from "vue"
import type {Panel} from "../../../src/utils/multiPanelTypes"
import {usePanelDefaultSize} from "../../../src/composables/usePanelDefaultSize"

function createPanel(size: number): Panel {
    const tab: Panel["activeTab"] = {
        uid: "tab",
        button: {icon: "", label: "Tab"},
        component: {},
    }

    return {
        size,
        tabs: [tab],
        activeTab: tab,
    }
}

describe("usePanelDefaultSize", () => {
    it("returns 1 for an empty panel list", () => {
        const panelSize = usePanelDefaultSize(ref<Panel[]>([]))

        expect(panelSize.value).toBe(1)
        expect(Number.isNaN(panelSize.value)).toBe(false)
    })

    it("returns the size of a single panel", () => {
        const panelSize = usePanelDefaultSize(ref([createPanel(40)]))

        expect(panelSize.value).toBe(40)
    })

    it("returns the arithmetic mean of multiple panel sizes", () => {
        const panelSize = usePanelDefaultSize(ref([createPanel(20), createPanel(40), createPanel(60)]))

        expect(panelSize.value).toBe(40)
    })

    it("counts a panel without a size as zero", () => {
        const panelWithoutSize = createPanel(0)
        Reflect.deleteProperty(panelWithoutSize, "size")
        const panelSize = usePanelDefaultSize(ref([createPanel(100), panelWithoutSize]))

        expect(() => panelSize.value).not.toThrow()
        expect(panelSize.value).toBe(50)
        expect(Number.isNaN(panelSize.value)).toBe(false)
    })

    it("updates the size when a panel is added to the reactive list", () => {
        const panels = ref([createPanel(20), createPanel(40)])
        const panelSize = usePanelDefaultSize(panels)

        expect(panelSize.value).toBe(30)

        panels.value.push(createPanel(60))

        expect(panelSize.value).toBe(40)
    })
})
