import {afterAll, beforeEach, describe, expect, it} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {defineComponent} from "vue"
import {mount} from "@vue/test-utils"
import {useVersionUpgradeNotice} from "../../../src/composables/useVersionUpgradeNotice"
import {useMiscStore} from "override/stores/misc"

const STORAGE_KEY = "kestra.versionUpgradeNotice"
const UPGRADE = {from: "1.3.4", to: "2.0.0", at: "2026-09-09T10:00:00Z"}

function setup(options: {configs?: Record<string, unknown>} = {}) {
    useMiscStore().configs = options.configs as never

    let api: ReturnType<typeof useVersionUpgradeNotice>
    mount(defineComponent({
        setup() {
            api = useVersionUpgradeNotice()
            return () => null
        },
    }))
    return api!
}

describe("useVersionUpgradeNotice", () => {
    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
    })

    afterAll(() => {
        localStorage.clear()
    })

    it("stays hidden when the backend reports no upgrade", () => {
        expect(setup({configs: {uuid: "i1"}}).visible.value).toBe(false)
    })

    it("shows the notice, then hides it once dismissed", () => {
        const {visible, dismiss} = setup({configs: {uuid: "i1", versionUpgrade: UPGRADE}})

        expect(visible.value).toBe(true)
        dismiss()
        expect(visible.value).toBe(false)
    })

    it("keeps the dismissal across reloads", () => {
        setup({configs: {uuid: "i1", versionUpgrade: UPGRADE}}).dismiss()

        setActivePinia(createPinia())
        expect(setup({configs: {uuid: "i1", versionUpgrade: UPGRADE}}).visible.value).toBe(false)
    })

    it("shows the notice again after a later upgrade", () => {
        setup({configs: {uuid: "i1", versionUpgrade: UPGRADE}}).dismiss()

        setActivePinia(createPinia())
        const next = setup({configs: {uuid: "i1", versionUpgrade: {...UPGRADE, from: "2.0.0", to: "2.1.0"}}})
        expect(next.visible.value).toBe(true)
    })

    it("ignores a dismissal recorded against another instance", () => {
        setup({configs: {uuid: "i1", versionUpgrade: UPGRADE}}).dismiss()

        setActivePinia(createPinia())
        expect(setup({configs: {uuid: "i2", versionUpgrade: UPGRADE}}).visible.value).toBe(true)
    })

    it("shows the notice when the stored state is corrupt", () => {
        localStorage.setItem(STORAGE_KEY, "{not json")

        expect(setup({configs: {uuid: "i1", versionUpgrade: UPGRADE}}).visible.value).toBe(true)
    })
})
