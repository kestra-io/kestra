import {beforeEach, describe, expect, it} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {KsTooltip} from "@kestra-io/design-system"
import {i18nMount} from "../../../tests/unit/i18nMount"
import Environment from "./Environment.vue"
import {useLayoutStore} from "../../stores/layout"
import {useMiscStore} from "override/stores/misc"

describe("Environment", () => {
    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
    })

    it("renders nothing when no environment name is configured", () => {
        const wrapper = i18nMount(Environment)
        expect(wrapper.find("#environment").exists()).toBe(false)
        expect(wrapper.findComponent(KsTooltip).exists()).toBe(false)
    })

    it("shows the full environment name in a tooltip wrapping the environment box", () => {
        const layoutStore = useLayoutStore()
        layoutStore.setEnvName("OSS - Test environment QA")

        const wrapper = i18nMount(Environment)

        const tooltip = wrapper.findComponent(KsTooltip)
        expect(tooltip.exists()).toBe(true)
        expect(tooltip.props("content")).toBe("OSS - Test environment QA")

        const envBox = wrapper.find("#environment")
        expect(envBox.exists()).toBe(true)
        expect(envBox.find(".name").text()).toBe("OSS - Test environment QA")
        expect(envBox.find(".name").attributes("title")).toBeUndefined()
        expect(envBox.find(".label").text()).toBe("environment")
    })

    it("displays the environment name from misc configs in the tooltip", () => {
        const miscStore = useMiscStore()
        miscStore.configs = {
            environment: {
                name: "Production - EU Cluster",
                color: "#ff0000",
            },
        }

        const wrapper = i18nMount(Environment)

        const tooltip = wrapper.findComponent(KsTooltip)
        expect(tooltip.exists()).toBe(true)
        expect(tooltip.props("content")).toBe("Production - EU Cluster")
        expect(wrapper.find("#environment .name").text()).toBe("Production - EU Cluster")
    })
})
