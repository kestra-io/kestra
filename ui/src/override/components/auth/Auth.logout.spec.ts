import {afterEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import {KsDropdown} from "@kestra-io/design-system"

const {purgeQueuedDrafts, basicLogout} = vi.hoisted(() => ({purgeQueuedDrafts: vi.fn(), basicLogout: vi.fn()}))

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {}, query: {}}),
    useRouter: () => ({push: vi.fn()}),
}))
vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({defaults: {headers: {common: {}}}}),
}))
vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {}}),
}))
vi.mock("../../../stores/flow", () => ({
    useFlowStore: () => ({purgeQueuedDrafts}),
}))
vi.mock("../../../utils/basicAuth", () => ({
    logout: basicLogout,
}))

import Auth from "./Auth.vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"

describe("Auth.vue - signing out", () => {
    let wrapper: VueWrapper | undefined

    afterEach(() => {
        wrapper?.unmount()
        wrapper = undefined
    })

    it("forgets the drafts kept on this device before the session ends", () => {
        wrapper = i18nMount(Auth, {
            global: {stubs: {ChangePasswordDialog: true, VersionMenuItem: true}},
        })

        wrapper.findComponent(KsDropdown).vm.$emit("command", "logout")

        expect(purgeQueuedDrafts).toHaveBeenCalledOnce()
        expect(basicLogout).toHaveBeenCalledOnce()
        expect(purgeQueuedDrafts.mock.invocationCallOrder[0]).toBeLessThan(basicLogout.mock.invocationCallOrder[0])
    })
})
