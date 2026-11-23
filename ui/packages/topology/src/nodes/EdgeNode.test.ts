import {describe, expect, it} from "vitest"
import {Position} from "@vue-flow/core"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import EdgeNode from "./EdgeNode.vue"

function mountLoopReturnEdge(isHorizontal = false) {
    return i18nMount(EdgeNode, {
        props: {
            id: "loop-return",
            data: {loopReturn: {right: 300, bottom: 420, entry: {x: 100, y: 100}}},
            sourceX: 100,
            sourceY: 200,
            targetX: 100,
            targetY: 100,
            sourcePosition: isHorizontal ? Position.Right : Position.Bottom,
            targetPosition: isHorizontal ? Position.Left : Position.Top,
            markerEnd: "marker-custom",
        },
    })
}

describe("EdgeNode loop return path", () => {
    it("routes a vertical return path along the right side of its lane", () => {
        const wrapper = mountLoopReturnEdge()
        const path = wrapper.get("path")

        expect(path.attributes("d")).toContain("300")
        expect(path.attributes("marker-end")).toBe("marker-custom")
    })

    it("routes a horizontal return path along the lower side of its lane", () => {
        const wrapper = mountLoopReturnEdge(true)
        const path = wrapper.get("path")

        expect(path.attributes("d")).toContain("420")
    })
})
