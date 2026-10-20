import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import {getIcon} from "material-file-icons"
import Type from "./Type.vue"
import {ICONS} from "./icons"

const decodeIcon = (src: string) => atob(src.replace("data:image/svg+xml;base64,", ""))

describe("file type icon", () => {
    it("renders the folder icon for a folder, whatever its name", () => {
        const wrapper = mount(Type, {props: {name: "some-folder", folder: true}})

        const src = wrapper.find("img").attributes("src")
        expect(src).toBe(`data:image/svg+xml;base64,${btoa(ICONS.FOLDER)}`)
        expect(decodeIcon(src!)).toBe(ICONS.FOLDER)
    })

    it("falls back to the Kestra icon for a name the icon library does not know", () => {
        const wrapper = mount(Type, {props: {name: "notes.unknownextension"}})

        const src = wrapper.find("img").attributes("src")
        expect(decodeIcon(src!)).toBe(ICONS.KESTRA)
    })

    it("renders the icon library icon for a recognised name", () => {
        const wrapper = mount(Type, {props: {name: "notes.py"}})

        const src = wrapper.find("img").attributes("src")
        expect(decodeIcon(src!)).toBe(getIcon("notes.py").svg)
        expect(decodeIcon(src!)).not.toBe(ICONS.KESTRA)
    })

    it("keeps the name as the alternative text", () => {
        const wrapper = mount(Type, {props: {name: "notes.py"}})

        expect(wrapper.find("img").attributes("alt")).toBe("notes.py")
    })
})
