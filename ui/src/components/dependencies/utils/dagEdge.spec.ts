import {describe, expect, it} from "vitest"
import {MarkerType} from "@xyflow/vue"
import {dagEdgeStyle} from "./dagEdge"

const resolve = (token: string) => `resolved:${token}`
const idle = {onPath: false, dimmed: false, backwards: false}

describe("dagEdgeStyle", () => {
    describe("stroke colour", () => {
        it.each([
            ["PRODUCES", "--ks-dependencies-edge-produces"],
            ["CONSUMED_BY", "--ks-dependencies-edge-consumed-by"],
            ["UPSTREAM_OF", "--ks-dependencies-edge-upstream-of"],
            ["PART_OF", "--ks-dependencies-edge-part-of"],
            ["RELATED", "--ks-dependencies-edge-related"],
        ])("strokes a %s edge with its relation kind token", (kind, token) => {
            expect(dagEdgeStyle({kind}, idle, resolve).style.stroke).toBe(`resolved:${token}`)
        })

        it("strokes a kindless edge with the link colour on the traced path", () => {
            expect(dagEdgeStyle({}, {...idle, onPath: true}, resolve).style.stroke).toBe("resolved:--ks-text-link")
        })

        it("strokes a kindless edge with the default border colour off the traced path", () => {
            expect(dagEdgeStyle({}, idle, resolve).style.stroke).toBe("resolved:--ks-border-default")
        })

        it("keeps the relation kind token over the link colour on the traced path", () => {
            expect(dagEdgeStyle({kind: "PRODUCES"}, {...idle, onPath: true}, resolve).style.stroke)
                .toBe("resolved:--ks-dependencies-edge-produces")
        })

        it("falls back to the kindless colour for an unrecognised kind", () => {
            expect(dagEdgeStyle({kind: "UNKNOWN"}, idle, resolve).style.stroke).toBe("resolved:--ks-border-default")
        })
    })

    describe("arrow marker", () => {
        it("draws no arrowhead for an undirected edge", () => {
            expect(dagEdgeStyle({kind: "RELATED", directed: false}, idle, resolve).markerEnd).toBeUndefined()
        })

        it("draws a closed arrowhead for a directed edge", () => {
            expect(dagEdgeStyle({kind: "PRODUCES", directed: true}, idle, resolve).markerEnd)
                .toEqual({type: MarkerType.ArrowClosed, color: "resolved:--ks-dependencies-edge-produces"})
        })

        it("draws a closed arrowhead when directed is omitted", () => {
            expect(dagEdgeStyle({}, {...idle, onPath: true}, resolve).markerEnd)
                .toEqual({type: MarkerType.ArrowClosed, color: "resolved:--ks-text-link"})
        })
    })

    describe("stroke width", () => {
        it("widens an edge on the traced path", () => {
            expect(dagEdgeStyle({}, {...idle, onPath: true}, resolve).style.strokeWidth).toBe(2)
        })

        it("keeps an edge off the traced path thin", () => {
            expect(dagEdgeStyle({}, idle, resolve).style.strokeWidth).toBe(1)
        })
    })

    describe("dash pattern", () => {
        it("dashes a backwards edge", () => {
            expect(dagEdgeStyle({}, {...idle, backwards: true}, resolve).style.strokeDasharray).toBe("6 4")
        })

        it("draws a forward edge solid", () => {
            expect(dagEdgeStyle({}, idle, resolve).style.strokeDasharray).toBeUndefined()
        })

        it("keeps the dash on a backwards edge that is on the traced path", () => {
            expect(dagEdgeStyle({}, {...idle, onPath: true, backwards: true}, resolve).style.strokeDasharray).toBe("6 4")
        })
    })

    describe("opacity", () => {
        it.each([
            {onPath: false, dimmed: true, backwards: true, opacity: 0.4},
            {onPath: false, dimmed: false, backwards: true, opacity: 0.65},
            {onPath: false, dimmed: false, backwards: false, opacity: 1},
            {onPath: true, dimmed: false, backwards: true, opacity: 1},
            {onPath: true, dimmed: true, backwards: true, opacity: 0.4},
        ])("gives onPath=$onPath dimmed=$dimmed backwards=$backwards an opacity of $opacity", ({opacity, ...state}) => {
            expect(dagEdgeStyle({}, state, resolve).style.opacity).toBe(opacity)
        })

        it("dims an edge outside the trace or filter", () => {
            expect(dagEdgeStyle({}, {...idle, dimmed: true}, resolve).style.opacity).toBe(0.4)
        })
    })
})
