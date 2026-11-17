import {beforeEach, describe, expect, it, vi} from "vitest"
import {ref} from "vue"

const {confirm} = vi.hoisted(() => ({
    confirm: vi.fn().mockResolvedValue(undefined),
}))
vi.mock("@kestra-io/design-system", () => ({
    KsMessageBox: {
        confirm: (...args: unknown[]) => confirm(...args),
    },
}))

const trackAuthoringAction = vi.fn()
vi.mock("../../../utils/tabTracking", () => ({
    trackAuthoringAction: (...args: unknown[]) => trackAuthoringAction(...args),
}))

import {
    blockDataAtPath,
    pathParentAndIndex,
    useBlockOperations,
    type BlockOperationsContext,
} from "./useBlockOperations"
import type {CanvasFocusApi} from "./useCanvasFocus"
import type {BlockClipboardApi} from "./useBlockClipboard"

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

function blockOrder(yaml: string): string[] {
    return [...yaml.matchAll(/id: (first|second)\b/g)].map(match => match[1])
}

function createFakeFocus(): CanvasFocusApi {
    return {
        focusedId: ref<string | undefined>(undefined),
        navigableCards: vi.fn(() => []),
        focusedCard: vi.fn(() => undefined),
        focusedAnchor: vi.fn(() => undefined),
        focusCanvasCard: vi.fn(),
        onCanvasFocusIn: vi.fn(),
        onCanvasEntryFocus: vi.fn(),
        moveFocus: vi.fn(),
        stepInto: vi.fn(),
        stepOut: vi.fn(),
        openFocused: vi.fn(),
        actionInFocused: vi.fn(),
        focusedBlockPath: vi.fn(() => undefined),
        focusedBlockDisplayName: vi.fn(() => ""),
        focusedBlockIsFlowable: vi.fn(() => false),
    } as unknown as CanvasFocusApi
}

function createFakeClipboard(): BlockClipboardApi {
    return {
        copy: vi.fn(),
        canPasteInto: vi.fn(() => false),
        pasteFor: vi.fn(() => undefined),
    }
}

function createFakeContext(overrides: Partial<BlockOperationsContext> = {}): BlockOperationsContext {
    return {
        t: (key: string) => key,
        flowYaml: ref(FLOW_YAML),
        applyYaml: vi.fn(),
        focus: createFakeFocus(),
        selectedId: ref<string | undefined>(undefined),
        selectedPath: ref<string | undefined>(undefined),
        sectionList: vi.fn((section) => {
            if (section === "tasks") {
                return [
                    {id: "first", type: "io.kestra.plugin.core.log.Log"},
                    {id: "second", type: "io.kestra.plugin.core.log.Log"},
                ]
            }
            return []
        }),
        isFlowable: vi.fn(() => false),
        deleteInSection: vi.fn(),
        deleteAtPath: vi.fn(),
        duplicateInSection: vi.fn(),
        duplicateAtPath: vi.fn(),
        clipboard: createFakeClipboard(),
        ...overrides,
    }
}

describe("useBlockOperations", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        confirm.mockResolvedValue(undefined)
    })

    describe("pathParentAndIndex", () => {
        it("a path ending in an index resolves to its parent path and that index", () => {
            expect(pathParentAndIndex("tasks[0]")).toEqual({parentPath: "tasks", index: 0})
            expect(pathParentAndIndex("tasks[2].tasks[1]")).toEqual({parentPath: "tasks[2].tasks", index: 1})
            expect(pathParentAndIndex("errors[3]")).toEqual({parentPath: "errors", index: 3})
        })

        it("a path with no trailing index resolves to nothing", () => {
            expect(pathParentAndIndex("tasks")).toBeUndefined()
            expect(pathParentAndIndex("")).toBeUndefined()
            expect(pathParentAndIndex("tasks.id")).toBeUndefined()
        })
    })

    describe("blockDataAtPath", () => {
        it("the block data at a path is read back, and a path pointing at nothing yields undefined", () => {
            expect(blockDataAtPath(FLOW_YAML, "tasks[0]")).toEqual(
                expect.objectContaining({id: "first", type: "io.kestra.plugin.core.log.Log"}),
            )
            expect(blockDataAtPath(FLOW_YAML, "tasks[1]")).toEqual(
                expect.objectContaining({id: "second", type: "io.kestra.plugin.core.log.Log"}),
            )
            expect(blockDataAtPath(FLOW_YAML, "tasks[99]")).toBeUndefined()
            expect(blockDataAtPath(FLOW_YAML, "triggers[0]")).toBeUndefined()
        })
    })

    describe("pasteTarget", () => {
        it("pasteTarget resolves from the focused block when there is one and from the selection otherwise", () => {
            const ctx = createFakeContext()
            const ops = useBlockOperations(ctx)

            ctx.selectedId.value = "second"
            ctx.selectedPath.value = "tasks[1]"
            expect(ops.pasteTarget()).toEqual({
                section: "tasks",
                parentPath: "tasks",
                refIndex: 1,
            })

            ctx.focus.focusedId.value = "first"
            vi.mocked(ctx.focus.focusedBlockPath).mockReturnValue("tasks[0]")
            expect(ops.pasteTarget()).toEqual({
                section: "tasks",
                parentPath: "tasks",
                refIndex: 0,
            })
        })

        it("pasteTarget resolves a selection that has no path from its section and position", () => {
            const ctx = createFakeContext()
            const ops = useBlockOperations(ctx)

            expect(ops.pasteTarget()).toBeUndefined()

            ctx.selectedId.value = "second"
            expect(ops.pasteTarget()).toEqual({
                section: "tasks",
                parentPath: "tasks",
                refIndex: 1,
            })
        })
    })

    describe("canPasteHere", () => {
        it("canPasteHere is false with an empty clipboard and false when there is no target", () => {
            const ctx = createFakeContext()
            const ops = useBlockOperations(ctx)

            expect(ops.canPasteHere()).toBe(false)

            vi.mocked(ctx.clipboard.canPasteInto).mockReturnValue(true)
            expect(ops.canPasteHere()).toBe(false)

            ctx.focus.focusedId.value = "first"
            vi.mocked(ctx.focus.focusedBlockPath).mockReturnValue("tasks[0]")
            vi.mocked(ctx.clipboard.canPasteInto).mockReturnValue(false)
            expect(ops.canPasteHere()).toBe(false)

            vi.mocked(ctx.clipboard.canPasteInto).mockReturnValue(true)
            expect(ops.canPasteHere()).toBe(true)
        })
    })

    describe("pasteRelative", () => {
        it("pasteRelative returns false when it cannot paste and leaves the YAML untouched", () => {
            const ctx = createFakeContext()
            const ops = useBlockOperations(ctx)

            expect(ops.pasteRelative()).toBe(false)
            expect(ctx.applyYaml).not.toHaveBeenCalled()

            vi.mocked(ctx.clipboard.pasteFor).mockReturnValue({id: "copied", type: "io.kestra.plugin.core.log.Log"})
            expect(ops.pasteRelative()).toBe(false)
            expect(ctx.clipboard.pasteFor).not.toHaveBeenCalled()
            expect(ctx.applyYaml).not.toHaveBeenCalled()

            ctx.focus.focusedId.value = "first"
            vi.mocked(ctx.focus.focusedBlockPath).mockReturnValue("tasks[0]")
            vi.mocked(ctx.clipboard.pasteFor).mockReturnValue(undefined)

            expect(ops.pasteRelative()).toBe(false)
            expect(ctx.applyYaml).not.toHaveBeenCalled()
        })
    })

    describe("move", () => {
        it("moving up at the first position, and down at the last, does nothing", () => {
            const ctx = createFakeContext()
            const ops = useBlockOperations(ctx)

            ctx.selectedId.value = "first"
            ctx.selectedPath.value = "tasks[0]"
            ops.moveSelected("up")
            expect(ctx.applyYaml).not.toHaveBeenCalled()
            expect(ctx.selectedPath.value).toBe("tasks[0]")

            ctx.selectedId.value = "second"
            ctx.selectedPath.value = "tasks[1]"
            ops.moveSelected("down")
            expect(ctx.applyYaml).not.toHaveBeenCalled()
            expect(ctx.selectedPath.value).toBe("tasks[1]")

            vi.mocked(ctx.focus.focusedBlockPath).mockReturnValue("tasks[0]")
            ops.moveFocused("up")
            expect(ctx.applyYaml).not.toHaveBeenCalled()

            vi.mocked(ctx.focus.focusedBlockPath).mockReturnValue("tasks[1]")
            ops.moveFocused("down")
            expect(ctx.applyYaml).not.toHaveBeenCalled()
        })

        it("a move otherwise applies YAML with the block in its new position", () => {
            const ctx = createFakeContext()
            const ops = useBlockOperations(ctx)

            ctx.selectedId.value = "first"
            ctx.selectedPath.value = "tasks[0]"
            ops.moveSelected("down")

            expect(ctx.applyYaml).toHaveBeenCalledTimes(1)
            expect(ctx.selectedPath.value).toBe("tasks[1]")

            const appliedYaml = vi.mocked(ctx.applyYaml).mock.calls[0][0]
            const firstIndex = appliedYaml.indexOf("id: first")
            const secondIndex = appliedYaml.indexOf("id: second")
            expect(secondIndex).toBeLessThan(firstIndex)
        })

        it("a move of a block selected without a path applies YAML with the block in its new position", () => {
            const ctx = createFakeContext()
            const ops = useBlockOperations(ctx)

            ctx.selectedId.value = "first"
            ops.moveSelected("down")

            expect(ctx.applyYaml).toHaveBeenCalledTimes(1)
            expect(blockOrder(vi.mocked(ctx.applyYaml).mock.calls[0][0])).toEqual(["second", "first"])
        })

        it("a move of the focused block applies YAML with the block in its new position", () => {
            const ctx = createFakeContext()
            const ops = useBlockOperations(ctx)

            vi.mocked(ctx.focus.focusedBlockPath).mockReturnValue("tasks[1]")
            ops.moveFocused("up")

            expect(ctx.applyYaml).toHaveBeenCalledTimes(1)
            expect(blockOrder(vi.mocked(ctx.applyYaml).mock.calls[0][0])).toEqual(["second", "first"])
        })
    })
})
