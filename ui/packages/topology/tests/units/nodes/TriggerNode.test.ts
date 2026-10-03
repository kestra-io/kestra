import {describe, expect, it} from "vitest"
import {defineComponent, h} from "vue"
import {Handle} from "@vue-flow/core"
import {TASK_ICON_INJECTION_KEY} from "@kestra-io/design-system"
import TriggerNode from "../../../src/nodes/TriggerNode.vue"
import BasicNode from "../../../src/nodes/BasicNode.vue"
import NodeMenu from "../../../src/nodes/NodeMenu.vue"
import {i18nMount} from "../../../../../tests/unit/i18nMount"

const TaskIconStub = defineComponent({
    name: "TaskIconStub",
    setup() {
        return () => h("span")
    },
})

function mountTriggerNode({
    trigger,
    triggerDeclaration,
    color,
    isReadOnly = false,
    stubBasicNode = false,
}: {
    trigger?: Record<string, unknown>,
    triggerDeclaration?: Record<string, unknown>,
    color?: string,
    isReadOnly?: boolean,
    stubBasicNode?: boolean,
} = {}) {
    return i18nMount(TriggerNode, {
        props: {
            id: "root.Triggers.schedule",
            data: {
                node: {trigger, triggerDeclaration},
                color,
                isReadOnly,
            },
        },
        global: {
            stubs: {
                Handle: true,
                ...(stubBasicNode
                    ? {
                          BasicNode: defineComponent({
                              name: "BasicNode",
                              props: {id: String, data: Object, color: String},
                              setup(_, {slots}) {
                                  return () => h("div", slots)
                              },
                          }),
                      }
                    : {}),
            },
            provide: {[TASK_ICON_INJECTION_KEY as symbol]: TaskIconStub},
        },
    })
}

function actionKeys(wrapper: ReturnType<typeof mountTriggerNode>) {
    return wrapper.findComponent(NodeMenu).props("actions").map((action: {key: string}) => action.key)
}

describe("TriggerNode", () => {
    it("should render the id segment after the last dot", () => {
        const wrapper = mountTriggerNode()

        expect(wrapper.findComponent(BasicNode).find(".task-title").text()).toBe("schedule")
    })

    it("should mark the node unused when the trigger declaration is disabled", () => {
        const wrapper = mountTriggerNode({triggerDeclaration: {disabled: true}})

        expect(wrapper.findComponent(BasicNode).props("data").unused).toBe(true)
    })

    it("should mark the node unused when the trigger is disabled", () => {
        const wrapper = mountTriggerNode({trigger: {disabled: true}})

        expect(wrapper.findComponent(BasicNode).props("data").unused).toBe(true)
    })

    it("should leave unused falsy when neither trigger is disabled", () => {
        const wrapper = mountTriggerNode()

        expect(wrapper.findComponent(BasicNode).props("data").unused).toBeFalsy()
    })

    it("should default to primary and use the provided color", () => {
        expect(mountTriggerNode({stubBasicNode: true}).findComponent(BasicNode).props("color")).toBe("primary")
        expect(mountTriggerNode({color: "danger", stubBasicNode: true}).findComponent(BasicNode).props("color")).toBe("danger")
    })

    it("should render actions and omit editing actions for read-only nodes", () => {
        expect(actionKeys(mountTriggerNode({trigger: {description: "A schedule"}}))).toEqual(["description", "edit", "delete"])

        const readOnlyKeys = actionKeys(mountTriggerNode({trigger: {description: "A schedule"}, isReadOnly: true}))
        expect(readOnlyKeys).toEqual(["description"])
    })

    it("should render source and target handles", () => {
        const handles = mountTriggerNode().findAllComponents(Handle)

        expect(handles).toHaveLength(2)
        expect(handles.map((handle) => handle.props("type"))).toEqual(["source", "target"])
    })
})
