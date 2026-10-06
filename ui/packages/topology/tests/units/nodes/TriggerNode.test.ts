import {defineComponent} from "vue"
import {describe, expect, it} from "vitest"
import {Handle, Position} from "@vue-flow/core"
import NodeMenu from "../../../src/nodes/NodeMenu.vue"
import TriggerNode from "../../../src/nodes/TriggerNode.vue"
import {EVENTS} from "../../../src/utils/constants"
import {SECTIONS} from "@kestra-io/design-system"
import {i18nMount} from "../../../../../tests/unit/i18nMount"

type MenuAction = {
    key: string;
    onClick: () => void;
};

// Light stub so we only test what TriggerNode passes down.
// It still renders the title-actions slot because that's where the menu lives.
const BasicNodeStub = defineComponent({
    name: "BasicNode",
    props: {
        data: {
            type: Object,
            required: true,
        },
        color: String,
    },
    template: "<div><slot name=\"title-actions\" /></div>",
})

const scheduleTrigger = {
    type: "io.kestra.plugin.core.trigger.Schedule",
}

function mountTriggerNode(props: Record<string, unknown> = {}) {
    return i18nMount(TriggerNode, {
        props: {
            id: "root.my-trigger",
            data: {
                node: {
                    trigger: {...scheduleTrigger},
                },
                color: "default",
            },
            sourcePosition: Position.Right,
            targetPosition: Position.Left,
            ...props,
        },
        global: {
            stubs: {
                Handle: true,
                BasicNode: BasicNodeStub,
            },
        },
    })
}

function getActions(wrapper: ReturnType<typeof mountTriggerNode>) {
    return wrapper.findComponent(NodeMenu).props("actions") as MenuAction[]
}

function getBasicNodeData(wrapper: ReturnType<typeof mountTriggerNode>) {
    return wrapper.findComponent(BasicNodeStub).props("data") as {
        unused?: boolean;
    }
}

describe("TriggerNode", () => {
    describe("trigger ID", () => {
        it("should use the part of the id after the last dot", () => {
            const wrapper = mountTriggerNode({id: "root.workflow.my-trigger"})

            const deleteAction = getActions(wrapper).find(
                (action) => action.key === "delete",
            )

            expect(deleteAction).toBeDefined()
            deleteAction?.onClick()

            // The emitted id is the shortened one, not the full "root.workflow.my-trigger".
            expect(wrapper.emitted(EVENTS.DELETE)).toEqual([
                [{id: "my-trigger", section: SECTIONS.TRIGGERS}],
            ])
        })
    })

    // TriggerNode reads "disabled" from either triggerDeclaration or trigger,
    // so each source gets its own test to catch a regression in one of them.
    describe("unused state", () => {
        it("should be unused when triggerDeclaration is disabled", () => {
            const wrapper = mountTriggerNode({
                data: {
                    node: {
                        triggerDeclaration: {...scheduleTrigger, disabled: true},
                    },
                    color: "default",
                },
            })

            expect(getBasicNodeData(wrapper).unused).toBe(true)
        })

        it("should be unused when trigger is disabled", () => {
            const wrapper = mountTriggerNode({
                data: {
                    node: {
                        trigger: {...scheduleTrigger, disabled: true},
                    },
                    color: "default",
                },
            })

            expect(getBasicNodeData(wrapper).unused).toBe(true)
        })

        it("should not be unused when neither is disabled", () => {
            const wrapper = mountTriggerNode({
                data: {
                    node: {
                        trigger: {...scheduleTrigger, disabled: false},
                        triggerDeclaration: {...scheduleTrigger, disabled: false},
                    },
                    color: "default",
                },
            })

            expect(getBasicNodeData(wrapper).unused).toBeFalsy()
        })
    })

    describe("color", () => {
        it("should default to primary when no color is given", () => {
            // No color in data on purpose
            const wrapper = mountTriggerNode({
                data: {
                    node: {
                        trigger: {...scheduleTrigger},
                    },
                },
            })

            expect(wrapper.findComponent(BasicNodeStub).props("color")).toBe("primary")
        })

        it("should use the color from data when present", () => {
            const wrapper = mountTriggerNode({
                data: {
                    node: {
                        trigger: {...scheduleTrigger},
                    },
                    color: "success",
                },
            })

            expect(wrapper.findComponent(BasicNodeStub).props("color")).toBe("success")
        })
    })

    describe("actions", () => {
        it("should render the action menu", () => {
            const wrapper = mountTriggerNode()

            expect(wrapper.findComponent(NodeMenu).exists()).toBe(true)
        })

        it("should add a description action when the trigger has a description", () => {
            const wrapper = mountTriggerNode({
                data: {
                    node: {
                        trigger: {
                            ...scheduleTrigger,
                            description: "Run the workflow every day",
                        },
                    },
                    color: "default",
                },
            })

            const descriptionAction = getActions(wrapper).find(
                (action) => action.key === "description",
            )

            expect(descriptionAction).toBeDefined()
            descriptionAction?.onClick()

            expect(wrapper.emitted(EVENTS.SHOW_DESCRIPTION)).toEqual([
                [{id: "my-trigger", description: "Run the workflow every day"}],
            ])
        })

        it("should offer edit and delete when not read-only", () => {
            const wrapper = mountTriggerNode({
                data: {
                    node: {
                        trigger: {...scheduleTrigger},
                        triggerDeclaration: {...scheduleTrigger},
                    },
                    color: "default",
                    isReadOnly: false,
                },
            })

            const keys = getActions(wrapper).map((action) => action.key)

            expect(keys).toContain("edit")
            expect(keys).toContain("delete")
        })

        it("should not offer edit and delete when read-only", () => {
            const wrapper = mountTriggerNode({
                data: {
                    node: {
                        trigger: {...scheduleTrigger},
                    },
                    color: "default",
                    isReadOnly: true,
                },
            })

            const keys = getActions(wrapper).map((action) => action.key)

            expect(keys).not.toContain("edit")
            expect(keys).not.toContain("delete")
        })
    })

    describe("handles", () => {
        it("should render a source and a target handle", () => {
            const wrapper = mountTriggerNode()

            const types = wrapper
                .findAllComponents(Handle)
                .map((handle) => handle.props("type"))

            // Not checking order, only that both exist
            expect(types).toHaveLength(2)
            expect(types).toEqual(expect.arrayContaining(["source", "target"]))
        })
    })
})