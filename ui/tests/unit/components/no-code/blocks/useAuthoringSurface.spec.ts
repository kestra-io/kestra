import {describe, it, expect} from "vitest"
import {defineComponent, h, KeepAlive, ref, type Ref} from "vue"
import {mount} from "@vue/test-utils"

import {authoringSurfaceAnswersKeyFor, useAuthoringSurface} from "../../../../../src/components/no-code/blocks/useAuthoringSurface"

interface Surface {
    isActive: () => boolean
    engage: () => void
}

const surfaces = new Map<string, Surface>()

const SurfaceComponent = defineComponent({
    props: {label: {type: String, required: true}},
    setup(props) {
        const root: Ref<HTMLElement | undefined> = ref()
        const {isActive} = useAuthoringSurface(root)
        surfaces.set(props.label, {
            isActive,
            engage: () => root.value?.dispatchEvent(new PointerEvent("pointerdown", {bubbles: true})),
        })
        return () => h("div", {ref: root})
    },
})

function mountSurface(label: string) {
    return mount(SurfaceComponent, {props: {label}, attachTo: document.body})
}

function surface(label: string): Surface {
    const found = surfaces.get(label)
    if (!found) throw new Error(`no surface named ${label}`)
    return found
}

describe("useAuthoringSurface", () => {
    // The Flow Code editor shares the window listener for Ctrl+S, so it asks whether this surface
    // answers for a target rather than guessing from its own containment.
    describe("authoringSurfaceAnswersKeyFor", () => {
        it("answers for its own canvas", () => {
            const wrapper = mountSurface("owner-canvas")
            const child = document.createElement("span")
            wrapper.element.appendChild(child)

            expect(authoringSurfaceAnswersKeyFor(child)).toBe(true)

            wrapper.unmount()
        })

        it("answers for a field in one of its teleported overlays", () => {
            const wrapper = mountSurface("owner-overlay")
            const overlay = document.createElement("div")
            overlay.setAttribute("data-authoring-overlay", "")
            const input = document.createElement("input")
            overlay.appendChild(input)
            document.body.appendChild(overlay)

            expect(authoringSurfaceAnswersKeyFor(input)).toBe(true)

            document.body.removeChild(overlay)
            wrapper.unmount()
        })

        // The gap this closes: a field belonging to neither has to be left to its own owner, or
        // the two window listeners both stand down and the chord reaches the browser.
        it("stands down for a field in an unrelated panel", () => {
            const wrapper = mountSurface("owner-foreign")
            const foreign = document.createElement("input")
            document.body.appendChild(foreign)

            expect(authoringSurfaceAnswersKeyFor(foreign)).toBe(false)

            document.body.removeChild(foreign)
            wrapper.unmount()
        })

        it("stands down entirely once no surface is mounted", () => {
            const wrapper = mountSurface("owner-gone")
            const child = document.createElement("span")
            wrapper.element.appendChild(child)
            wrapper.unmount()

            expect(authoringSurfaceAnswersKeyFor(child)).toBe(false)
        })
    })

    it("stays active while it is the only surface", () => {
        const only = mountSurface("only")
        expect(surface("only").isActive()).toBe(true)
        only.unmount()
    })

    it("gives a shortcut to the surface the user last engaged", () => {
        const first = mountSurface("first")
        const second = mountSurface("second")

        surface("first").engage()
        expect(surface("first").isActive()).toBe(true)
        expect(surface("second").isActive()).toBe(false)

        surface("second").engage()
        expect(surface("first").isActive()).toBe(false)
        expect(surface("second").isActive()).toBe(true)

        first.unmount()
        second.unmount()
    })

    it("elects exactly one surface when none has been engaged", () => {
        const first = mountSurface("first")
        const second = mountSurface("second")

        const active = [surface("first").isActive(), surface("second").isActive()]
        expect(active.filter(Boolean)).toHaveLength(1)

        first.unmount()
        second.unmount()
    })

    it("hands the shortcut on when the engaged surface unmounts", () => {
        const first = mountSurface("first")
        const second = mountSurface("second")

        surface("first").engage()
        first.unmount()

        expect(surface("second").isActive()).toBe(true)
        second.unmount()
    })

    it("hands the shortcut on when the engaged surface is only deactivated", async () => {
        const shown = ref("first")
        const host = mount(
            defineComponent({
                setup() {
                    return () => h(KeepAlive, null, {
                        default: () => h(SurfaceComponent, {key: shown.value, label: shown.value}),
                    })
                },
            }),
            {attachTo: document.body},
        )

        surface("first").engage()
        expect(surface("first").isActive()).toBe(true)

        shown.value = "second"
        await host.vm.$nextTick()

        expect(surface("second").isActive()).toBe(true)
        expect(surface("first").isActive()).toBe(false)

        host.unmount()
    })
})
