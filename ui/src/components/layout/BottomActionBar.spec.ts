import {afterEach, describe, expect, it, vi} from "vitest"
import {i18nMount} from "../../../tests/unit/i18nMount"
import BottomActionBar from "./BottomActionBar.vue"

const stubMatchMedia = (matches: boolean) => vi.stubGlobal("matchMedia", (query: string) => ({
    matches,
    media: query,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
}))

const mountBar = () => {
    document.body.innerHTML = "<div id=\"bottom-actions-slot\"></div>"
    return i18nMount(BottomActionBar, {
        messages: {actions: "Actions"},
        slots: {default: "<button>Restart</button>", more: "<button>More</button>"},
        attachTo: document.body,
    })
}

describe("BottomActionBar", () => {
    afterEach(() => {
        vi.unstubAllGlobals()
        document.body.innerHTML = ""
    })

    it("renders a labelled toolbar in the bottom slot on a phone", async () => {
        // Given
        stubMatchMedia(true)

        // When
        const wrapper = mountBar()
        await wrapper.vm.$nextTick()

        // Then
        const toolbar = document.querySelector("#bottom-actions-slot [role=\"toolbar\"]")
        expect(toolbar?.getAttribute("aria-label")).toBe("Actions")
        expect(toolbar?.textContent).toContain("Restart")
        expect(toolbar?.textContent).toContain("More")
    })

    it("renders nothing on a larger screen", async () => {
        // Given
        stubMatchMedia(false)

        // When
        const wrapper = mountBar()
        await wrapper.vm.$nextTick()

        // Then
        expect(document.querySelector("#bottom-actions-slot")?.children).toHaveLength(0)
    })
})
