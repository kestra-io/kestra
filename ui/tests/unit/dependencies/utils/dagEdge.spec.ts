import {describe, it, expect} from "vitest"
import {dagEdgeStyle} from "../../../../src/components/dependencies/utils/dagEdge"

const resolve = (token: string) => `var(${token})`
const idle = {onPath: false, dimmed: false, backwards: false}

describe("dagEdgeStyle", () => {
    it("strokes an edge with its relation kind token", () => {
        expect(dagEdgeStyle({kind: "RELATED"}, idle, resolve).style.stroke).toBe("var(--ks-dependencies-edge-related)")
    })

    it("highlights a kindless edge on the traced path and widens it", () => {
        const onPath = dagEdgeStyle({}, {...idle, onPath: true}, resolve).style
        const offPath = dagEdgeStyle({}, idle, resolve).style

        expect(onPath.stroke).toBe("var(--ks-text-link)")
        expect(onPath.strokeWidth).toBe(2)
        expect(offPath.stroke).toBe("var(--ks-border-default)")
        expect(offPath.strokeWidth).toBe(1)
    })

    it("draws no arrowhead for an undirected edge", () => {
        expect(dagEdgeStyle({kind: "RELATED", directed: false}, idle, resolve).markerEnd).toBeUndefined()
        expect(dagEdgeStyle({kind: "PRODUCES", directed: true}, idle, resolve).markerEnd).toBeDefined()
    })

    it("dims an edge outside the trace or filter and softens a backwards one", () => {
        expect(dagEdgeStyle({}, {...idle, dimmed: true}, resolve).style.opacity).toBe(0.4)
        const backwards = dagEdgeStyle({}, {...idle, backwards: true}, resolve).style
        expect(backwards.opacity).toBe(0.65)
        expect(backwards.strokeDasharray).toBe("6 4")
    })
})
