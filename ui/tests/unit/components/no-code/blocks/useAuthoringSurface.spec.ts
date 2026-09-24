import {describe, it, expect} from "vitest"
import {defineComponent, h, KeepAlive, ref, type Ref} from "vue"
import {mount} from "@vue/test-utils"

import {hasActiveAuthoringSurface, useAuthoringSurface} from "../../../../../src/components/no-code/blocks/useAuthoringSurface"

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
    // The Flow Code editor yields Ctrl+S to an authoring surface, so it needs to know one is up.
    it("reports whether any surface is mounted", () => {
        expect(hasActiveAuthoringSurface()).toBe(false)

        const wrapper = mountSurface("ownership")
        expect(hasActiveAuthoringSurface()).toBe(true)

        wrapper.unmount()
        expect(hasActiveAuthoringSurface()).toBe(false)
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
