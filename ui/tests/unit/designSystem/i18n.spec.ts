import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem, {registerDesignSystemI18n} from "@kestra-io/design-system"
import KsEmpty from "@kestra-io/design-system/components/Data/KsEmpty.vue"

const mountKsEmpty = (locale: string) => {
    const i18n = createI18n({legacy: false, locale, messages: {}})

    // No await: the merge has to be done by the time the first render reads t(),
    // or parent computeds cache the raw key.
    registerDesignSystemI18n(i18n)

    return mount(KsEmpty, {global: {plugins: [i18n, KestraDesignSystem]}})
}

describe("registerDesignSystemI18n", () => {
    it("renders a design-system string on first render", () => {
        expect(mountKsEmpty("en").text()).toContain("Looks like there's nothing here")
    })

    it("renders a design-system string translated for a non-English locale", () => {
        expect(mountKsEmpty("fr").text()).toContain("On dirait qu'il n'y a rien ici")
    })
})
