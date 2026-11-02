import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import * as monaco from "monaco-editor/editor/editor.api"
import {typescriptDefaults, ScriptTarget} from "monaco-editor/languages/features/typescript/register"
import {
    configureMonacoTypescript,
    editorModelUid,
    getOrCreateOverflowWidgetsDomNode,
    OVERFLOW_WIDGETS_ID,
    registerMonacoThemes,
} from "./monacoSetup"

vi.mock("monaco-editor/editor/editor.api", () => ({
    editor: {
        defineTheme: vi.fn(),
    },
}))

vi.mock("monaco-editor/languages/features/typescript/register", () => ({
    typescriptDefaults: {
        setCompilerOptions: vi.fn(),
    },
    ScriptTarget: {
        ES2020: 7,
    },
}))

describe("monacoSetup", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        document.body.innerHTML = ""
    })

    afterEach(() => {
        vi.restoreAllMocks()
    })

    test("uses the expected overflow widgets container ID", () => {
        expect(OVERFLOW_WIDGETS_ID).toBe("ks-monaco-overflow-widgets")
    })

    describe("registerMonacoThemes", () => {
        test("registers the dark and light themes", () => {
            registerMonacoThemes()

            expect(monaco.editor.defineTheme).toHaveBeenCalledTimes(2)
            expect(monaco.editor.defineTheme).toHaveBeenCalledWith(
                "dark",
                expect.objectContaining({base: "vs-dark"}),
            )
            expect(monaco.editor.defineTheme).toHaveBeenCalledWith(
                "light",
                expect.objectContaining({base: "vs"}),
            )
        })

        test("does not throw when registering themes twice", () => {
            expect(() => {
                registerMonacoThemes()
                registerMonacoThemes()
            }).not.toThrow()
        })
    })

    describe("configureMonacoTypescript", () => {
        test("sets the TypeScript compiler options", () => {
            configureMonacoTypescript()

            expect(typescriptDefaults.setCompilerOptions).toHaveBeenCalledWith({
                target: ScriptTarget.ES2020,
                lib: ["es2020"],
                allowNonTsExtensions: true,
            })
        })
    })

    describe("getOrCreateOverflowWidgetsDomNode", () => {
        test("creates and appends the node when it does not exist", () => {
            const node = getOrCreateOverflowWidgetsDomNode()

            expect(node).toBeInstanceOf(HTMLDivElement)
            expect(node.id).toBe(OVERFLOW_WIDGETS_ID)
            expect(node.className).toBe("monaco-editor")
            expect(document.body.contains(node)).toBe(true)
        })

        test("reuses the existing node", () => {
            const existingNode = document.createElement("div")
            existingNode.id = OVERFLOW_WIDGETS_ID
            document.body.appendChild(existingNode)

            const node = getOrCreateOverflowWidgetsDomNode()

            expect(node).toBe(existingNode)
            expect(document.querySelectorAll(`#${OVERFLOW_WIDGETS_ID}`)).toHaveLength(1)
        })

        test("returns the same container when called by different editors", () => {
            const firstEditorNode = getOrCreateOverflowWidgetsDomNode()
            const secondEditorNode = getOrCreateOverflowWidgetsDomNode()

            expect(secondEditorNode).toBe(firstEditorNode)
            expect(document.querySelectorAll(`#${OVERFLOW_WIDGETS_ID}`)).toHaveLength(1)
        })
    })

    describe("editorModelUid", () => {
        test("returns a string containing up to nine lowercase alphanumeric characters", () => {
            const randomSpy = vi.spyOn(Math, "random").mockReturnValue(0.123456789)

            const uid = editorModelUid()

            expect(uid).toMatch(/^[a-z0-9]{1,9}$/)
            randomSpy.mockRestore()
        })

        test("returns different IDs for different random values", () => {
            vi.spyOn(Math, "random")
                .mockReturnValueOnce(0.123456789)
                .mockReturnValueOnce(0.987654321)

            expect(editorModelUid()).not.toBe(editorModelUid())
        })
    })
})
