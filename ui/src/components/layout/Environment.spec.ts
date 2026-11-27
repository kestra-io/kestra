import {afterEach, beforeEach, describe, expect, it} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import type {MiscControllerConfiguration} from "@kestra-io/kestra-sdk"
import {i18nMount} from "../../../tests/unit/i18nMount"
import Environment from "./Environment.vue"
import {useLayoutStore} from "../../stores/layout"
import {useMiscStore} from "override/stores/misc"

describe("Environment", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        localStorage.clear()
    })

    it("shouldNotRenderWhenEnvironmentNameIsNotConfigured", () => {
        const wrapper = i18nMount(Environment)

        expect(wrapper.find("#environment").exists()).toBe(false)
        expect(wrapper.findComponent({name: "KsTooltip"}).exists()).toBe(false)
    })

    it("shouldRenderEnvironmentBoxWrappedInTooltipWhenLayoutEnvNameIsSet", () => {
        const layoutStore = useLayoutStore()
        layoutStore.envName = "OSS - Test environment QA"

        const wrapper = i18nMount(Environment)

        const tooltip = wrapper.findComponent({name: "KsTooltip"})
        expect(tooltip.exists()).toBe(true)
        expect(tooltip.props("content")).toBe("OSS - Test environment QA")

        const envBox = wrapper.find("#environment")
        expect(envBox.exists()).toBe(true)
        expect(envBox.find(".name").text()).toBe("OSS - Test environment QA")
        expect(envBox.find(".label").text()).toBe("environment")
    })

    it("shouldFallbackToMiscStoreConfigWhenLayoutEnvNameIsNotSet", () => {
        const miscStore = useMiscStore()
        miscStore.configs = {
            environment: {
                name: "Production Cluster EU",
                color: "#28a745",
            },
        } as unknown as MiscControllerConfiguration

        const wrapper = i18nMount(Environment)

        const tooltip = wrapper.findComponent({name: "KsTooltip"})
        expect(tooltip.exists()).toBe(true)
        expect(tooltip.props("content")).toBe("Production Cluster EU")

        const envBox = wrapper.find("#environment")
        expect(envBox.exists()).toBe(true)
        expect(envBox.find(".name").text()).toBe("Production Cluster EU")
    })

    it("shouldPreferLayoutEnvNameToMiscStoreConfig", () => {
        const layoutStore = useLayoutStore()
        layoutStore.envName = "Overridden Environment Name"

        const miscStore = useMiscStore()
        miscStore.configs = {
            environment: {
                name: "Base Environment Name",
            },
        } as unknown as MiscControllerConfiguration

        const wrapper = i18nMount(Environment)

        const tooltip = wrapper.findComponent({name: "KsTooltip"})
        expect(tooltip.props("content")).toBe("Overridden Environment Name")
        expect(wrapper.find(".name").text()).toBe("Overridden Environment Name")
    })
})
