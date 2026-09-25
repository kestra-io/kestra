import {describe, it, expect, afterEach} from "vitest"
import {defineComponent, h, ref, type Ref} from "vue"
import {mount} from "@vue/test-utils"

import {useBlockEditorKeyboard} from "../../../../../src/components/no-code/blocks/useBlockEditorKeyboard"
import {
    authoringSurfaceAnswersKeyFor,
    useAuthoringSurface,
} from "../../../../../src/components/no-code/blocks/useAuthoringSurface"

const KEYMAP = [{id: "save", keys: ["Meta+s", "Control+s"]}]

const dispatched: string[] = []
let surfaceRoot: HTMLElement | undefined

const Surface = defineComponent({
    setup() {
        const root: Ref<HTMLElement | undefined> = ref()
        const surface = useAuthoringSurface(root)
        useBlockEditorKeyboard({
            keymap: KEYMAP,
            dispatch: id => {
                if (!surface.isActive()) return false
                dispatched.push(id)
                return undefined
            },
            root,
        })
        return () => h("div", {ref: el => {
            root.value = el as HTMLElement
            surfaceRoot = root.value
        }}, [h("span", {class: "canvas-child"})])
    },
})

// `FlowFileEditorTab.handleGlobalSave`, reduced to the decision under test.
function editorAnswers(target: EventTarget | null): boolean {
    return !authoringSurfaceAnswersKeyFor(target as Node)
}

function attach(el: HTMLElement): HTMLElement {
    document.body.appendChild(el)
    return el
}

function input(): HTMLInputElement {
    return document.createElement("input")
}

describe("the Ctrl+S chord has exactly one owner", () => {
    const created: HTMLElement[] = []
    let wrapper: ReturnType<typeof mount> | undefined

    afterEach(() => {
        dispatched.length = 0
        created.splice(0).forEach(el => el.remove())
        wrapper?.unmount()
        wrapper = undefined
        surfaceRoot = undefined
    })

    function track<T extends HTMLElement>(el: T): T {
        created.push(el)
        return el
    }

    function answersFor(target: EventTarget): {surface: boolean; editor: boolean} {
        dispatched.length = 0
        const event = new KeyboardEvent("keydown", {key: "s", metaKey: true, bubbles: true, cancelable: true})
        target.dispatchEvent(event)
        return {surface: dispatched.includes("save"), editor: editorAnswers(target)}
    }

    const cases: [string, () => EventTarget][] = [
        ["nothing focused (body)", () => document.body],
        ["the canvas itself", () => surfaceRoot!.querySelector(".canvas-child")!],
        ["a field inside the surface", () => {
            const el = input()
            surfaceRoot!.appendChild(el)
            return el
        }],
        ["a field in a teleported authoring overlay", () => {
            const overlay = track(attach(document.createElement("div")))
            overlay.setAttribute("data-authoring-overlay", "")
            const el = input()
            overlay.appendChild(el)
            return el
        }],
        ["a Monaco editor outside the surface", () => {
            const monaco = track(attach(document.createElement("div")))
            monaco.className = "monaco-editor"
            const el = document.createElement("textarea")
            monaco.appendChild(el)
            return el
        }],
        ["a third panel's text field", () => track(attach(input()))],
    ]

    it.each(cases)("with a surface mounted, %s is answered once", (_label, makeTarget) => {
        wrapper = mount(Surface, {attachTo: document.body})

        const {surface, editor} = answersFor(makeTarget())

        expect({surface, editor}, "exactly one of the two window listeners must answer").toSatisfy(
            (r: {surface: boolean; editor: boolean}) => r.surface !== r.editor,
        )
    })

    it("hands every target to the editor when no surface is mounted", () => {
        const targets = [document.body, track(attach(input()))]

        for (const target of targets) {
            expect(editorAnswers(target), "editor answers alone").toBe(true)
        }
        expect(dispatched).toEqual([])
    })

    it("dispatches nothing from the surface for a chord the editor owns", () => {
        wrapper = mount(Surface, {attachTo: document.body})
        const monaco = track(attach(document.createElement("div")))
        monaco.className = "monaco-editor"
        const el = document.createElement("textarea")
        monaco.appendChild(el)

        const {surface, editor} = answersFor(el)

        expect(surface).toBe(false)
        expect(editor).toBe(true)
    })
})
