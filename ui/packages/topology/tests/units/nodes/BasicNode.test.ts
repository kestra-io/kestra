import {describe, expect, it, vi} from "vitest"
import {defineComponent, h} from "vue"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {TASK_ICON_INJECTION_KEY} from "@kestra-io/design-system"
import BasicNode from "../../../src/nodes/BasicNode.vue"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {en: {}},
    missingWarn: false,
    fallbackWarn: false,
})

const CLS = "io.kestra.plugin.core.log.Log"

// Records the props the app-provided task-icon component is mounted with.
const iconProps: Record<string, unknown>[] = []
const TaskIconSpy = defineComponent({
    name: "TaskIconSpy",
    inheritAttrs: false,
    props: {cls: {type: String, default: undefined}, icons: {type: Object, default: undefined}, loadIcon: {type: Function, default: undefined}},
    setup(props) {
        iconProps.push({cls: props.cls, icons: props.icons, loadIcon: props.loadIcon})
        return () => h("img")
    },
})

function mountBasicNode(props: Record<string, unknown> = {}, slots: Record<string, string> = {}) {
    return mount(BasicNode, {
        props: {
            id: "root.my-task",
            data: {node: {task: {id: "my-task", type: CLS}}, color: "default"},
            icons: {},
            ...props,
        },
        global: {
            plugins: [i18n],
            // KsTooltip wraps the title in an element-plus popper; render only its default slot.
            stubs: {KsTooltip: {template: "<span><slot /></span>"}},
            provide: {[TASK_ICON_INJECTION_KEY as symbol]: TaskIconSpy},
        },
        slots,
    })
}

describe("BasicNode icons", () => {
    it("should hand the task-icon component the loadIcon resolver so a class missing from the icons index can still be fetched", () => {
        // Given an icons index that doesn't carry this class — the plugin isn't in the local index
        iconProps.length = 0
        const loadIcon = vi.fn().mockResolvedValue(undefined)

        // When the node renders
        mountBasicNode({icons: {}, loadIcon})

        // Then the icon component gets the resolver, not just the empty index
        expect(iconProps).toHaveLength(1)
        expect(iconProps[0].cls).toBe(CLS)
        expect(iconProps[0].loadIcon).toBe(loadIcon)
    })

    it("should still render without a loadIcon resolver", () => {
        iconProps.length = 0

        mountBasicNode({icons: {[CLS]: {flowable: false, monochrome: false, hasIcon: true}}})

        expect(iconProps[0].loadIcon).toBeUndefined()
        expect(iconProps[0].icons).toHaveProperty(CLS)
    })
})

describe("BasicNode layout", () => {
    const slots = {
        badge: "<span class='badge-marker'>badge</span>",
        "title-status": "<span class='status-marker'>status</span>",
        "title-actions": "<span class='actions-marker'>actions</span>",
    }

    it("should render the badge above the title, outside the title row", () => {
        const wrapper = mountBasicNode({}, slots)

        expect(wrapper.find(".node-content > .badge-marker").exists()).toBe(true)
        expect(wrapper.find(".node-title .badge-marker").exists()).toBe(false)
    })

    it("should render the status and actions as direct children of the main content", () => {
        const wrapper = mountBasicNode({}, slots)

        expect(wrapper.find(".main-content > .status-marker").exists()).toBe(true)
        expect(wrapper.find(".main-content > .actions-marker").exists()).toBe(true)

        expect(wrapper.find(".node-content .status-marker").exists()).toBe(false)
        expect(wrapper.find(".node-content .actions-marker").exists()).toBe(false)
    })
})
