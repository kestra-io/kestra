import {beforeEach, describe, expect, test, vi} from "vitest"
import {mount, type VueWrapper} from "@vue/test-utils"
import Type from "./Type.vue"
import {ICONS} from "./icons"

const {getIcon, PY_SVG, GENERIC_SVG} = vi.hoisted(() => ({
    getIcon: vi.fn(),
    PY_SVG: "<svg>python</svg>",
    GENERIC_SVG: "<svg>generic</svg>",
}))

vi.mock("material-file-icons", () => ({getIcon}))

const PREFIX = "data:image/svg+xml;base64,"

function decodedSvg(wrapper: VueWrapper) {
    const src = wrapper.get("img").attributes("src") ?? ""
    expect(src.slice(0, PREFIX.length)).toBe(PREFIX)
    return atob(src.slice(PREFIX.length))
}

describe("Type", () => {
    beforeEach(() => {
        getIcon.mockClear()
        getIcon.mockImplementation((name: string) =>
            name === "script.py" ? {name: "python", svg: PY_SVG} : {name: "file", svg: GENERIC_SVG},
        )
    })

    test("renders the folder icon regardless of the name", () => {
        const wrapper = mount(Type, {props: {name: "script.py", folder: true}})

        expect(decodedSvg(wrapper)).toBe(ICONS.FOLDER)
        expect(getIcon).not.toHaveBeenCalled()
    })

    test("falls back to the Kestra icon for an unrecognised name", () => {
        const wrapper = mount(Type, {props: {name: "notes.unknown"}})

        expect(decodedSvg(wrapper)).toBe(ICONS.KESTRA)
    })

    test("renders the library icon for a recognised name", () => {
        const wrapper = mount(Type, {props: {name: "script.py"}})

        expect(decodedSvg(wrapper)).toBe(PY_SVG)
        expect(getIcon).toHaveBeenCalledWith("script.py")
    })

    test("renders the name as alt at width 18", () => {
        const img = mount(Type, {props: {name: "script.py"}}).get("img")

        expect(img.attributes("alt")).toBe("script.py")
        expect(img.attributes("width")).toBe("18")
    })

    test("recomputes the icon when the name changes", async () => {
        const wrapper = mount(Type, {props: {name: "notes.unknown"}})
        expect(decodedSvg(wrapper)).toBe(ICONS.KESTRA)

        await wrapper.setProps({name: "script.py"})

        expect(decodedSvg(wrapper)).toBe(PY_SVG)
    })
})
