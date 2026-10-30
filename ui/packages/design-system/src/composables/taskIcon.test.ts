import {defineComponent, h} from "vue"
import {describe, expect, test, vi} from "vitest"
import {mount} from "@vue/test-utils"

import {
    TASK_ICON_INJECTION_KEY,
    taskIconProps,
    useTaskIcon,
} from "./taskIcon"

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
        const warn = vi.spyOn(console, "warn").mockImplementation(() => {})
        const loadIcon = async () => undefined
        const passedProps = {
            cls: "test",
            customIcon: {icon: "test", monochrome: true},
            icons: {test: {flowable: false, monochrome: false, hasIcon: true}},
            onlyIcon: true,
            variable: "test",
            loadIcon,
        }

        try {
            const Consumer = defineComponent({
                setup() {
                    return {taskIcon: useTaskIcon()}
                },
                render() {
                    return h(this.taskIcon, passedProps)
                },
            })

            const icon = mount(Consumer).findComponent({name: "FallbackTaskIcon"})

            expect(warn).not.toHaveBeenCalled()
            expect(Object.keys(icon.props()).sort()).toEqual(Object.keys(taskIconProps).sort())
            expect(icon.props("cls")).toBe("test")
            expect(icon.props("onlyIcon")).toBe(true)
            expect(icon.props("variable")).toBe("test")
            expect(icon.props("loadIcon")).toBe(loadIcon)
        } finally {
            warn.mockRestore()
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