import {describe, expect, it} from "vitest"
import {Handle, Position} from "@vue-flow/core"
import DotNode from "../../../src/nodes/DotNode.vue"
import {i18nMount} from "../../../../../tests/unit/i18nMount"

function mountDotNode({type = "io.kestra.plugin.core.flow.If", unused = false, branchType, sourcePosition, targetPosition}: {
    type?: string,
    unused?: boolean,
    branchType?: string,
    sourcePosition?: Position,
    targetPosition?: Position,
} = {}) {
    return i18nMount(DotNode, {
        props: {
            data: {unused, node: {type, branchType}},
            sourcePosition,
            targetPosition,
        },
        global: {
            stubs: {Handle: true},
        },
    })
}

describe("DotNode", () => {
    it("should derive the root class from the segment after the last dot", () => {
        const wrapper = mountDotNode({type: "io.kestra.plugin.core.flow.If"})

        expect(wrapper.classes()).toContain("If")
        expect(wrapper.find(".dot").classes()).toContain("If")
    })

    it("should toggle the unused-path class from the unused flag", () => {
        const unused = mountDotNode({unused: true})
        const used = mountDotNode({unused: false})

        expect(unused.classes()).toContain("unused-path")
        expect(unused.find(".dot").classes()).toContain("unused-path")
        expect(used.classes()).not.toContain("unused-path")
        expect(used.find(".dot").classes()).not.toContain("unused-path")
    })

    it("should mark only error branches as dangerous", () => {
        expect(mountDotNode({branchType: "ERROR"}).find(".circle").classes()).toContain("text-danger")
        expect(mountDotNode({branchType: "FINALLY"}).find(".circle").classes()).not.toContain("text-danger")
        expect(mountDotNode().find(".circle").classes()).not.toContain("text-danger")
    })

    it("should render source and target handles", () => {
        const handles = mountDotNode().findAllComponents(Handle)

        expect(handles).toHaveLength(2)
        expect(handles.map((handle) => handle.props("type"))).toEqual(["source", "target"])
    })

    it("should pass the source and target positions to the matching handles", () => {
        const wrapper = mountDotNode({sourcePosition: Position.Right, targetPosition: Position.Left})
        const [source, target] = wrapper.findAllComponents(Handle)

        expect(source.props("position")).toBe(Position.Right)
        expect(target.props("position")).toBe(Position.Left)
    })

    it("should use a node type without dots as a class", () => {
        const wrapper = mountDotNode({type: "If"})

        expect(wrapper.classes()).toContain("If")
    })
})
