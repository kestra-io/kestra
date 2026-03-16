import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsBreadcrumb from "../../../src/components/Navigation/KsBreadcrumb/KsBreadcrumb.vue"
import KsBreadcrumbItem from "../../../src/components/Navigation/KsBreadcrumb/KsBreadcrumbItem.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsBreadcrumb", () => {
    test("renders breadcrumb element", () => {
        const wrapper = mount(KsBreadcrumb, {
            global: globalConfig,
        })
        expect(wrapper.find(".kel-breadcrumb").exists()).toBe(true)
    })

    test("renders breadcrumb items", () => {
        const wrapper = mount({
            components: {KsBreadcrumb, KsBreadcrumbItem},
            template: `
                <ks-breadcrumb separator="/">
                    <ks-breadcrumb-item>Home</ks-breadcrumb-item>
                    <ks-breadcrumb-item>Flows</ks-breadcrumb-item>
                </ks-breadcrumb>
            `,
        }, {global: globalConfig})
        expect(wrapper.findAll(".kel-breadcrumb__item").length).toBe(2)
    })

    test("renders custom separator", () => {
        const wrapper = mount(KsBreadcrumb, {
            props: {separator: ">"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-breadcrumb").exists()).toBe(true)
    })
})
