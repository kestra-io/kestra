import {describe, expect, it} from "vitest"
import {defineComponent, h} from "vue"
import {Handle, Position} from "@vue-flow/core"
import {TASK_ICON_INJECTION_KEY} from "@kestra-io/design-system"
import TriggerNode from "../../../src/nodes/TriggerNode.vue"
import NodeMenu from "../../../src/nodes/NodeMenu.vue"
import {i18nMount} from "../../../../../tests/unit/i18nMount"

const TRIGGER_TYPE = "io.kestra.plugin.core.trigger.Schedule"

interface TriggerDefinition {
    type?: string;
    description?: string;
    disabled?: boolean;
}

const BasicNodeStub = defineComponent({
    name: "BasicNode",
    props: {
        id: {type: String, default: undefined},
        data: {type: Object, default: undefined},
        color: {type: String, default: undefined},
    },
    template: `<div class="basic-node-stub"><slot name="title-actions" /></div>`,
})

const TaskIconSpy = defineComponent({
    name: "TaskIconSpy",
    inheritAttrs: false,
    props: {
        cls: {type: String, default: undefined},
        icons: {type: Object, default: undefined},
        loadIcon: {type: Function, default: undefined},
    },
    setup: () => () => h("img"),
})

function triggerData({
    triggerDeclaration,
    trigger,
    color,
    isReadOnly = false,
}: {
    triggerDeclaration?: TriggerDefinition;
    trigger?: TriggerDefinition;
    color?: string;
    isReadOnly?: boolean;
} = {}) {
    return {
        ...(color === undefined ? {} : {color}),
        isReadOnly,
        node: {
            ...(triggerDeclaration ? {triggerDeclaration} : {}),
            ...(trigger ? {trigger} : {}),
        },
    }
}

function mountTriggerNode({
    id = "root.flow.my-trigger",
    data = triggerData({triggerDeclaration: {type: TRIGGER_TYPE}}),
    sourcePosition,
    targetPosition,
    realBasicNode = false,
}: {
    id?: string;
    data?: ReturnType<typeof triggerData>;
    sourcePosition?: Position;
    targetPosition?: Position;
    realBasicNode?: boolean;
} = {}) {
    return i18nMount(TriggerNode, {
        props: {id, data, sourcePosition, targetPosition},
        global: realBasicNode
            ? {
                stubs: {
                    Handle: true,
                    NodeMenu: true,
                    KsTooltip: {template: "<span><slot /></span>"},
                },
                provide: {[TASK_ICON_INJECTION_KEY as symbol]: TaskIconSpy},
            }
            : {
                stubs: {
                    BasicNode: BasicNodeStub,
                    Handle: true,
                    NodeMenu: true,
                },
            },
    })
}

function passedData(wrapper: ReturnType<typeof mountTriggerNode>) {
    return wrapper.findComponent(BasicNodeStub).props("data") as {unused?: boolean}
}

function actionKeys(wrapper: ReturnType<typeof mountTriggerNode>) {
    return wrapper.findComponent(NodeMenu).props("actions").map((action: {key: string}) => action.key)
}

describe("TriggerNode title", () => {
    it("should render the id shortened to the segment after the last dot", () => {
        const wrapper = mountTriggerNode({id: "root.flow.my-trigger", realBasicNode: true})

        expect(wrapper.find(".task-title").text()).toBe("my-trigger")
    })
})

describe("TriggerNode unused", () => {
    it("should mark the node unused when triggerDeclaration.disabled is set", () => {
        const wrapper = mountTriggerNode({
            data: triggerData({triggerDeclaration: {type: TRIGGER_TYPE, disabled: true}}),
        })

        expect(passedData(wrapper).unused).toBe(true)
    })

    it("should mark the node unused when trigger.disabled is set instead", () => {
        const wrapper = mountTriggerNode({
            data: triggerData({
                triggerDeclaration: {type: TRIGGER_TYPE},
                trigger: {type: TRIGGER_TYPE, disabled: true},
            }),
        })

        expect(passedData(wrapper).unused).toBe(true)
    })

    it("should leave unused falsy when neither trigger is disabled", () => {
        const wrapper = mountTriggerNode({
            data: triggerData({triggerDeclaration: {type: TRIGGER_TYPE}}),
        })

        expect(passedData(wrapper).unused).toBeFalsy()
    })
})

describe("TriggerNode color", () => {
    it("should default the colour to primary when data.color is absent", () => {
        const wrapper = mountTriggerNode({
            data: triggerData({triggerDeclaration: {type: TRIGGER_TYPE}}),
        })

        expect(wrapper.findComponent(BasicNodeStub).props("color")).toBe("primary")
    })

    it("should use data.color when it is present", () => {
        const wrapper = mountTriggerNode({
            data: triggerData({triggerDeclaration: {type: TRIGGER_TYPE}, color: "success"}),
        })

        expect(wrapper.findComponent(BasicNodeStub).props("color")).toBe("success")
    })
})

describe("TriggerNode actions", () => {
    it("should render the action menu with edit and delete when the trigger is editable", () => {
        const wrapper = mountTriggerNode({
            data: triggerData({
                triggerDeclaration: {type: TRIGGER_TYPE, description: "Runs periodically"},
                isReadOnly: false,
            }),
        })

        expect(wrapper.findComponent(NodeMenu).exists()).toBe(true)
        expect(actionKeys(wrapper)).toEqual(["description", "edit", "delete"])
    })

    it("should not offer edit or delete when the trigger is read-only", () => {
        const wrapper = mountTriggerNode({
            data: triggerData({
                triggerDeclaration: {type: TRIGGER_TYPE, description: "Runs periodically"},
                isReadOnly: true,
            }),
        })

        const keys = actionKeys(wrapper)
        expect(wrapper.findComponent(NodeMenu).exists()).toBe(true)
        expect(keys).toEqual(["description"])
        expect(keys).not.toContain("edit")
        expect(keys).not.toContain("delete")
    })
})

describe("TriggerNode handles", () => {
    it("should render source and target handles at the given positions", () => {
        const wrapper = mountTriggerNode({
            sourcePosition: Position.Right,
            targetPosition: Position.Left,
        })
        const handles = wrapper.findAllComponents(Handle)

        expect(handles).toHaveLength(2)
        expect(handles.map((handle) => handle.props("type"))).toEqual(["source", "target"])
        expect(handles[0].props("position")).toBe(Position.Right)
        expect(handles[1].props("position")).toBe(Position.Left)
    })
})
