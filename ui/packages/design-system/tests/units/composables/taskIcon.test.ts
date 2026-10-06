import {defineComponent, h} from "vue"
import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"

import {
    TASK_ICON_INJECTION_KEY,
    taskIconProps,
    useTaskIcon,
} from "../../../src/composables/taskIcon"

describe("useTaskIcon", () => {
    test("returns the component provided under TASK_ICON_INJECTION_KEY", () => {
        const ProvidedTaskIcon = defineComponent({
            name: "ProvidedTaskIcon",
            render: () => h("span", "provided"),
        })

        const Consumer = defineComponent({
            setup() {
                return {taskIcon: useTaskIcon()}
            },
            render() {
                return h(this.taskIcon)
            },
        })

        const wrapper = mount(Consumer, {
            global: {
                provide: {
                    [TASK_ICON_INJECTION_KEY as symbol]: ProvidedTaskIcon,
                },
            },
        })

        expect(wrapper.findComponent(ProvidedTaskIcon).exists()).toBe(true)
    })

    test("returns the fallback component when nothing is provided", () => {
        const Consumer = defineComponent({
            setup() {
                return {taskIcon: useTaskIcon()}
            },
            render() {
                return h(this.taskIcon)
            },
        })

        const wrapper = mount(Consumer)

        expect(wrapper.find("img").exists()).toBe(true)
    })

    test("fallback accepts every task-icon prop without warning", () => {
        const warnings: unknown[][] = []
        const originalWarn = console.warn

        console.warn = (...args) => {
            warnings.push(args)
        }

        try {
            const Consumer = defineComponent({
                setup() {
                    return {taskIcon: useTaskIcon()}
                },
                render() {
                    return h(this.taskIcon, {
                        cls: "test",
                        customIcon: {
                            icon: "test",
                            monochrome: true,
                        },
                        icons: {
                            test: "icon",
                        },
                        onlyIcon: true,
                        variable: "test",
                        loadIcon: async () => "icon",
                    })
                },
            })

            mount(Consumer)

            expect(warnings).toEqual([])
        } finally {
            console.warn = originalWarn
        }
    })

    test("declares the expected task-icon prop defaults", () => {
        expect(taskIconProps.cls.default).toBeUndefined()
        expect(taskIconProps.customIcon.default).toBeUndefined()
        expect(taskIconProps.icons.default).toBeUndefined()
        expect(taskIconProps.onlyIcon.default).toBe(false)
        expect(taskIconProps.variable.default).toBeUndefined()
        expect(taskIconProps.loadIcon.default).toBeUndefined()
    })

    test("returns the same component to two consumers under the same provider", () => {
        const ProvidedTaskIcon = defineComponent({
            name: "ProvidedTaskIcon",
            render: () => h("span"),
        })

        const resolvedIcons: unknown[] = []

        const Consumer = defineComponent({
            setup() {
                resolvedIcons.push(useTaskIcon())
                return () => null
            },
        })

        const Parent = defineComponent({
            render: () => h("div", [h(Consumer), h(Consumer)]),
        })

        mount(Parent, {
            global: {
                provide: {
                    [TASK_ICON_INJECTION_KEY as symbol]: ProvidedTaskIcon,
                },
            },
        })

        expect(resolvedIcons).toEqual([ProvidedTaskIcon, ProvidedTaskIcon])
        expect(resolvedIcons[0]).toBe(resolvedIcons[1])
    })
})