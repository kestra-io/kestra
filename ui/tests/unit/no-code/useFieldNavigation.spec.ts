import {describe, it, expect} from "vitest"
import {useFieldNavigation, type NavFrame} from "../../../src/components/no-code/utils/useFieldNavigation"

const frame = (path: string): NavFrame => ({path, label: path, schema: {}})

describe("useFieldNavigation", () => {
    it("starts empty", () => {
        const nav = useFieldNavigation()
        expect(nav.stack.value).toEqual([])
        expect(nav.current.value).toBeUndefined()
    })

    it("push tracks current and depth", () => {
        const nav = useFieldNavigation()
        nav.push(frame("inputs"))
        nav.push(frame("inputs.0"))
        expect(nav.stack.value).toHaveLength(2)
        expect(nav.current.value?.path).toBe("inputs.0")
    })

    it("pop removes the top frame", () => {
        const nav = useFieldNavigation()
        nav.push(frame("a"))
        nav.push(frame("b"))
        nav.pop()
        expect(nav.stack.value).toHaveLength(1)
        expect(nav.current.value?.path).toBe("a")
    })

    it("popTo slices to the given depth", () => {
        const nav = useFieldNavigation()
        nav.push(frame("a"))
        nav.push(frame("b"))
        nav.push(frame("c"))
        nav.popTo(0)
        expect(nav.stack.value.map(f => f.path)).toEqual(["a"])
    })

    it("reset clears the stack", () => {
        const nav = useFieldNavigation()
        nav.push(frame("a"))
        nav.reset()
        expect(nav.stack.value).toEqual([])
        expect(nav.current.value).toBeUndefined()
    })
})
