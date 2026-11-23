import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {nextTick, ref} from "vue"

const {opensInModalByDefault} = vi.hoisted(() => ({
    opensInModalByDefault: vi.fn(() => true),
}))
vi.mock("./taskEditMode", () => ({
    opensInModalByDefault: () => opensInModalByDefault(),
}))

import {useBlockSelection, modalItemPathOf, type BlockSelectionContext} from "./useBlockSelection"

const FLOW_YAML = [
    "id: my-flow",
    "namespace: company.team",
    "tasks:",
    "  - id: first",
    "    type: io.kestra.plugin.core.log.Log",
    "    message: hello",
    "  - id: second",
    "    type: io.kestra.plugin.core.log.Log",
    "    message: world",
    "",
].join("\n")

function createEditorElement(): HTMLElement {
    const editor = document.createElement("div")
    const cardFirst = document.createElement("div")
    cardFirst.setAttribute("data-block-id", "first")
    cardFirst.scrollIntoView = vi.fn()

    const cardSecond = document.createElement("div")
    cardSecond.setAttribute("data-block-id", "second")
    cardSecond.scrollIntoView = vi.fn()

    editor.appendChild(cardFirst)
    editor.appendChild(cardSecond)
    return editor
}

function createFakeContext(overrides: Partial<BlockSelectionContext> = {}): BlockSelectionContext {
    const taskFirst = {id: "first", type: "io.kestra.plugin.core.log.Log"}
    const taskSecond = {id: "second", type: "io.kestra.plugin.core.log.Log"}

    return {
        selectedId: ref<string | undefined>(undefined),
        editorEl: ref<HTMLElement | undefined>(createEditorElement()),
        flowYaml: ref(FLOW_YAML),
        flowSchemaRoot: ref("#/definitions/Flow"),
        sectionList: vi.fn((section) => {
            if (section === "tasks") return [taskFirst, taskSecond]
            return []
        }),
        onSelectedIdChange: vi.fn(),
        onEditTask: vi.fn(),
        onCloseTask: vi.fn(),
        ...overrides,
    }
}

const stubMatchMedia = (matches = false) => {
    vi.stubGlobal("matchMedia", (query: string) => ({
        matches,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
    }))
}

describe("useBlockSelection", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        opensInModalByDefault.mockReturnValue(true)
        stubMatchMedia(false)
    })

    afterEach(() => {
        vi.unstubAllGlobals()
    })

    it("re-exports modalItemPathOf from useTaskEditModalStack", () => {
        expect(modalItemPathOf({parentPath: "tasks", blockSchemaPath: "#/items", refPath: 1})).toBe("tasks[1]")
    })

    describe("activeSelectedId", () => {
        it("initializes from context selectedId", () => {
            const ctx = createFakeContext({selectedId: ref("first")})
            const {activeSelectedId} = useBlockSelection(ctx)

            expect(activeSelectedId.value).toBe("first")
        })

        it("notifies context onSelectedIdChange when updated", () => {
            const ctx = createFakeContext()
            const {activeSelectedId} = useBlockSelection(ctx)

            activeSelectedId.value = "second"

            expect(activeSelectedId.value).toBe("second")
            expect(ctx.onSelectedIdChange).toHaveBeenCalledWith("second")
        })
    })

    describe("watch selectedId", () => {
        it("scrolls card into view with smooth behavior by default", async () => {
            const ctx = createFakeContext()
            useBlockSelection(ctx)

            ctx.selectedId.value = "first"
            await nextTick()
            await nextTick()

            const card = ctx.editorEl.value?.querySelector("[data-block-id='first']")
            expect(card?.scrollIntoView).toHaveBeenCalledWith({block: "nearest", behavior: "smooth"})
        })

        it("scrolls card into view with auto behavior when prefers-reduced-motion matches", async () => {
            stubMatchMedia(true)
            const ctx = createFakeContext()
            useBlockSelection(ctx)

            ctx.selectedId.value = "first"
            await nextTick()
            await nextTick()

            const card = ctx.editorEl.value?.querySelector("[data-block-id='first']")
            expect(card?.scrollIntoView).toHaveBeenCalledWith({block: "nearest", behavior: "auto"})
        })

        it("does not scroll when selectedId is cleared", async () => {
            const ctx = createFakeContext({selectedId: ref("first")})
            useBlockSelection(ctx)

            ctx.selectedId.value = undefined
            await nextTick()
            await nextTick()

            const card = ctx.editorEl.value?.querySelector("[data-block-id='first']")
            expect(card?.scrollIntoView).not.toHaveBeenCalled()
        })
    })

    describe("blockSchemaPathFor", () => {
        it("formats the root schema path for a given section", () => {
            const ctx = createFakeContext({flowSchemaRoot: ref("#/definitions/io.kestra.core.models.flows.Flow")})
            const {blockSchemaPathFor} = useBlockSelection(ctx)

            expect(blockSchemaPathFor("tasks")).toBe("#/definitions/io.kestra.core.models.flows.Flow/properties/tasks/items")
            expect(blockSchemaPathFor("triggers")).toBe("#/definitions/io.kestra.core.models.flows.Flow/properties/triggers/items")
        })
    })

    describe("selectBlock", () => {
        it("ignores a block without an id", () => {
            const ctx = createFakeContext()
            const {selectBlock, activeSelectedId, modalStack} = useBlockSelection(ctx)

            selectBlock("tasks", {type: "io.kestra.plugin.core.log.Log"})

            expect(activeSelectedId.value).toBeUndefined()
            expect(modalStack.value).toEqual([])
            expect(ctx.onEditTask).not.toHaveBeenCalled()
        })

        it("ignores a block not present in sectionList", () => {
            const ctx = createFakeContext()
            const {selectBlock, activeSelectedId, modalStack} = useBlockSelection(ctx)

            selectBlock("tasks", {id: "non-existent", type: "io.kestra.plugin.core.log.Log"})

            expect(activeSelectedId.value).toBeUndefined()
            expect(modalStack.value).toEqual([])
            expect(ctx.onEditTask).not.toHaveBeenCalled()
        })

        it("selects block and opens modal target when modal mode is enabled", () => {
            const ctx = createFakeContext()
            const {selectBlock, activeSelectedId, activeSelectedPath, modalStack} = useBlockSelection(ctx)
            const block = ctx.sectionList("tasks")[0]

            selectBlock("tasks", block)

            expect(activeSelectedId.value).toBe("first")
            expect(activeSelectedPath.value).toBeUndefined()
            expect(modalStack.value).toEqual([{
                parentPath: "tasks",
                blockSchemaPath: "#/definitions/Flow/properties/tasks/items",
                refPath: 0,
            }])
            expect(ctx.onEditTask).not.toHaveBeenCalled()
        })

        it("calls onEditTask instead of modal when split is requested", () => {
            const ctx = createFakeContext()
            const {selectBlock, activeSelectedId, modalStack} = useBlockSelection(ctx)
            const block = ctx.sectionList("tasks")[1]

            selectBlock("tasks", block, true)

            expect(activeSelectedId.value).toBe("second")
            expect(modalStack.value).toEqual([])
            expect(ctx.onEditTask).toHaveBeenCalledWith("tasks", "#/definitions/Flow/properties/tasks/items", 1, true)
        })

        it("calls onEditTask when opensInModalByDefault is false", () => {
            opensInModalByDefault.mockReturnValue(false)
            const ctx = createFakeContext()
            const {selectBlock, activeSelectedId, modalStack} = useBlockSelection(ctx)
            const block = ctx.sectionList("tasks")[0]

            selectBlock("tasks", block)

            expect(activeSelectedId.value).toBe("first")
            expect(modalStack.value).toEqual([])
            expect(ctx.onEditTask).toHaveBeenCalledWith("tasks", "#/definitions/Flow/properties/tasks/items", 0, false)
        })
    })

    describe("openNestedEdit", () => {
        it("ignores a path pointing to a non-existent block", () => {
            const ctx = createFakeContext()
            const {openNestedEdit, activeSelectedId} = useBlockSelection(ctx)

            openNestedEdit("tasks[99]")

            expect(activeSelectedId.value).toBeUndefined()
            expect(ctx.onEditTask).not.toHaveBeenCalled()
        })

        it("ignores a path with no array index", () => {
            const ctx = createFakeContext()
            const {openNestedEdit, activeSelectedId} = useBlockSelection(ctx)

            openNestedEdit("tasks")

            expect(activeSelectedId.value).toBeUndefined()
            expect(ctx.onEditTask).not.toHaveBeenCalled()
        })

        it("selects nested block and opens modal target", () => {
            const ctx = createFakeContext()
            const {openNestedEdit, activeSelectedId, activeSelectedPath, modalStack} = useBlockSelection(ctx)

            openNestedEdit("tasks[0]")

            expect(activeSelectedId.value).toBe("first")
            expect(activeSelectedPath.value).toBe("tasks[0]")
            expect(modalStack.value).toEqual([{
                parentPath: "tasks",
                blockSchemaPath: "#/definitions/Flow/properties/tasks/items",
                refPath: 0,
            }])
        })

        it("forwards split to onEditTask when split is requested", () => {
            const ctx = createFakeContext()
            const {openNestedEdit, activeSelectedId, activeSelectedPath, modalStack} = useBlockSelection(ctx)

            openNestedEdit("tasks[1]", true)

            expect(activeSelectedId.value).toBe("second")
            expect(activeSelectedPath.value).toBe("tasks[1]")
            expect(modalStack.value).toEqual([])
            expect(ctx.onEditTask).toHaveBeenCalledWith("tasks", "#/definitions/Flow/properties/tasks/items", 1, true)
        })
    })

    describe("deselectIfCurrent", () => {
        it("clears selection and calls onCloseTask when id matches activeSelectedId", () => {
            const ctx = createFakeContext()
            const {deselectIfCurrent, activeSelectedId, activeSelectedPath} = useBlockSelection(ctx)

            activeSelectedId.value = "first"
            activeSelectedPath.value = "tasks[0]"

            deselectIfCurrent("first")

            expect(activeSelectedId.value).toBeUndefined()
            expect(activeSelectedPath.value).toBeUndefined()
            expect(ctx.onCloseTask).toHaveBeenCalledTimes(1)
        })

        it("does nothing when id does not match activeSelectedId", () => {
            const ctx = createFakeContext()
            const {deselectIfCurrent, activeSelectedId, activeSelectedPath} = useBlockSelection(ctx)

            activeSelectedId.value = "first"
            activeSelectedPath.value = "tasks[0]"

            deselectIfCurrent("second")

            expect(activeSelectedId.value).toBe("first")
            expect(activeSelectedPath.value).toBe("tasks[0]")
            expect(ctx.onCloseTask).not.toHaveBeenCalled()
        })
    })
})
