import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {i18nShallowMount} from "../../../tests/unit/i18nMount"
import {useAuthStore} from "override/stores/auth"
import action from "../../models/action"
import KVs from "./KVs.vue"

function hasCreate() {
    const wrapper = i18nShallowMount(KVs, {
        global: {stubs: {TopNavBar: {template: "<div><slot name=\"actions\" /></div>"}}},
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
