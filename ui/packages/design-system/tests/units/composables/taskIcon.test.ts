import {afterEach, describe, expect, test, vi} from "vitest"
import {defineComponent, h, provide, type Component} from "vue"
import {mount} from "@vue/test-utils"
import {
    TASK_ICON_INJECTION_KEY,
    taskIconProps,
    useTaskIcon,
} from "../../../src/composables/taskIcon"

const ProvidedTaskIcon = defineComponent({
    name: "ProvidedTaskIcon",
    render: () => h("span"),
})

afterEach(() => {
    vi.restoreAllMocks()
})

describe("useTaskIcon", () => {
    test("returns the component provided under TASK_ICON_INJECTION_KEY", () => {
        let resolved: Component | undefined

        const Consumer = defineComponent({
            setup() {
                resolved = useTaskIcon()
                return () => null
            },
        })

        mount(defineComponent({
            setup() {
                provide(TASK_ICON_INJECTION_KEY, ProvidedTaskIcon)
                return () => h(Consumer)
            },
        }))

        expect(resolved).toBe(ProvidedTaskIcon)
    })

    test("renders the fallback img when no component is provided", () => {
        const wrapper = mount(defineComponent({
            setup() {
                const TaskIcon = useTaskIcon()
                return () => h(TaskIcon)
            },
        }))

        expect(wrapper.find("img").exists()).toBe(true)
    })

    test("accepts all task icon props without warnings", () => {
        const warn = vi.spyOn(console, "warn").mockImplementation(() => {})

        mount(defineComponent({
            setup() {
                const TaskIcon = useTaskIcon()
                return () => h(TaskIcon, {
                    cls: "io.kestra.plugin.core.log.Log",
                    customIcon: {
                        icon: "custom-icon",
                        monochrome: true,
                    },
                    icons: {
                        log: "icon",
                    },
                    onlyIcon: true,
                    variable: "{{ task.type }}",
                    loadIcon: async () => undefined,
                })
            },
        }))

        expect(warn).not.toHaveBeenCalled()
    })

    test("declares the expected task icon prop defaults", () => {
        expect(taskIconProps.onlyIcon.default).toBe(false)
        expect(taskIconProps.cls.default).toBeUndefined()
        expect(taskIconProps.customIcon.default).toBeUndefined()
        expect(taskIconProps.icons.default).toBeUndefined()
        expect(taskIconProps.variable.default).toBeUndefined()
        expect(taskIconProps.loadIcon.default).toBeUndefined()
    })

    test("resolves the same provided component for multiple consumers", () => {
        const resolved: Component[] = []

        const Consumer = defineComponent({
            setup() {
                resolved.push(useTaskIcon())
                return () => null
            },
        })

        mount(defineComponent({
            setup() {
                provide(TASK_ICON_INJECTION_KEY, ProvidedTaskIcon)
                return () => [h(Consumer), h(Consumer)]
            },
        }))

        expect(resolved).toHaveLength(2)
        expect(resolved[0]).toBe(ProvidedTaskIcon)
        expect(resolved[1]).toBe(ProvidedTaskIcon)
        expect(resolved[0]).toBe(resolved[1])
    })
})
