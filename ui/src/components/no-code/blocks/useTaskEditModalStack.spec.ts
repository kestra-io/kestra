import {describe, it, expect} from "vitest"
import {modalItemPathOf, useTaskEditModalStack, type ModalTarget} from "./useTaskEditModalStack"

const PARENT: ModalTarget = {parentPath: "tasks", blockSchemaPath: "#/properties/tasks/items", refPath: 0}
const NESTED: ModalTarget = {parentPath: "tasks[0].then", blockSchemaPath: "#/properties/then/items", refPath: 1}
const CREATING: ModalTarget = {parentPath: "tasks[0].then[1].else", blockSchemaPath: "#/properties/else/items", creating: true}

describe("modalItemPathOf", () => {
    it.each([
        {refPath: 2, expected: "tasks[1].then[2]"},
        {refPath: 0, expected: "tasks[1].then[0]"},
        {refPath: undefined, expected: "tasks[1].then"},
    ])("returns $expected for refPath $refPath", ({refPath, expected}) => {
        expect(modalItemPathOf({parentPath: "tasks[1].then", blockSchemaPath: "#/properties/then/items", refPath})).toBe(expected)
    })
})

describe("useTaskEditModalStack", () => {
    it("starts with an empty stack and no active target", () => {
        const {modalStack, modalTarget} = useTaskEditModalStack()

        expect(modalStack.value).toEqual([])
        expect(modalTarget.value).toBeUndefined()
    })

    it("activates each pushed target while preserving its parent", () => {
        const {modalStack, modalTarget, pushModalTarget} = useTaskEditModalStack()

        pushModalTarget(PARENT)

        expect(modalStack.value).toEqual([PARENT])
        expect(modalTarget.value).toEqual(PARENT)

        pushModalTarget(NESTED)

        expect(modalStack.value).toEqual([PARENT, NESTED])
        expect(modalTarget.value).toEqual(NESTED)
    })

    it.each([0, 2])("resolves only the top target at refPath %i and clears creating", (refPath) => {
        const {modalStack, modalTarget, pushModalTarget, resolveCreatedTarget} = useTaskEditModalStack()
        pushModalTarget(PARENT)
        pushModalTarget(NESTED)
        pushModalTarget(CREATING)
        const resolved = {parentPath: "tasks[0].then[1].then", blockSchemaPath: "#/properties/then/items", refPath}

        expect(modalTarget.value).toEqual(CREATING)

        resolveCreatedTarget(resolved.parentPath, resolved.blockSchemaPath, resolved.refPath)

        expect(modalStack.value).toEqual([PARENT, NESTED, resolved])
        expect(modalTarget.value).toEqual(resolved)
        expect(modalTarget.value).not.toHaveProperty("creating")
    })

    it("leaves an empty stack unchanged when resolving a created target", () => {
        const {modalStack, modalTarget, resolveCreatedTarget} = useTaskEditModalStack()

        resolveCreatedTarget("tasks", "#/properties/tasks/items", 0)

        expect(modalStack.value).toEqual([])
        expect(modalTarget.value).toBeUndefined()
    })

    it("pops to the selected ancestor inclusively and activates it", () => {
        const {modalStack, modalTarget, pushModalTarget, popModalTo} = useTaskEditModalStack()
        pushModalTarget(PARENT)
        pushModalTarget(NESTED)
        pushModalTarget(CREATING)

        expect(modalTarget.value).toEqual(CREATING)

        popModalTo(1)

        expect(modalStack.value).toEqual([PARENT, NESTED])
        expect(modalTarget.value).toEqual(NESTED)
    })

    it("closes every modal and clears the active target", () => {
        const {modalStack, modalTarget, pushModalTarget, closeModal} = useTaskEditModalStack()
        pushModalTarget(PARENT)
        pushModalTarget(NESTED)
        pushModalTarget(CREATING)

        expect(modalTarget.value).toEqual(CREATING)

        closeModal()

        expect(modalStack.value).toEqual([])
        expect(modalTarget.value).toBeUndefined()
    })
})
