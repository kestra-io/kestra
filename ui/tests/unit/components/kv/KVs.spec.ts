import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {shallowMount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {useAuthStore} from "override/stores/auth"
import action from "../../../../src/models/action"
import KVs from "../../../../src/components/kv/KVs.vue"

const i18n = createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false, messages: {en: {}}})

function hasCreate() {
    const wrapper = shallowMount(KVs, {
        global: {plugins: [i18n], stubs: {TopNavBar: {template: "<div><slot name=\"actions\" /></div>"}}},
    })
    return wrapper.find("[data-test='kv-add']").exists()
}

describe("KVs", () => {
    let originalTitle: string

    beforeEach(() => {
        originalTitle = document.title
        setActivePinia(createPinia())
    })

    afterEach(() => {
        document.title = originalTitle
    })

    it("offers Create only to a user who can create a key in some namespace", () => {
        expect(hasCreate()).toBe(true)

        vi.spyOn(useAuthStore().user!, "hasAnyActionOnAnyNamespace").mockImplementation((_resource, actionName) => actionName !== action.CREATE)

        expect(hasCreate()).toBe(false)
    })
})
