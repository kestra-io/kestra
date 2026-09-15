import {describe, it, expect, vi} from "vitest"
import {mount} from "@vue/test-utils"

const notice = {value: undefined as {from: string, to: string, at: string} | undefined}
const visible = {value: true}
const dismiss = vi.fn()

vi.mock("../../../src/composables/useVersionUpgradeNotice", () => ({
    useVersionUpgradeNotice: () => ({notice, visible, dismiss}),
}))

import VersionUpgradeNotice from "../../../src/components/VersionUpgradeNotice.vue"

function mountFor(to: string) {
    notice.value = {from: "1.3.4", to, at: "2026-09-09T10:00:00Z"}
    return mount(VersionUpgradeNotice, {
        global: {
            mocks: {$t: (key: string) => key},
            stubs: {
                KsAlert: {template: "<div><slot name=\"title\" /><slot /></div>"},
                KsButton: {props: ["href"], template: "<a :href=\"href\"><slot /></a>"},
            },
        },
    })
}

function hrefFor(to: string) {
    return mountFor(to).get("a").attributes("href")
}

describe("VersionUpgradeNotice", () => {
    it("links to the migration guide for the new version", () => {
        expect(hrefFor("2.0.0")).toBe("https://kestra.io/docs/migration-guide/v2.0.0")
    })

    it("drops the button when the version names no release line", () => {
        // A version with no minor, or one the backend could not parse, has no guide to point at.
        expect(mountFor("2").find("a").exists()).toBe(false)
        expect(mountFor("Snapshot").find("a").exists()).toBe(false)
    })

    it("still announces the upgrade when the button is dropped", () => {
        expect(mountFor("Snapshot").text()).toContain("versionUpgradeNotice.message")
    })

    it("links to the .0 guide of the minor line, which is the only one published", () => {
        // Jumping 1.3.4 to 2.0.3 must still land on v2.0.0; there is no v2.0.3 guide.
        // What a backport to releases/v2.0.x runs: 2.0.5 must still point at the v2.0.0 guide.
        expect(hrefFor("2.0.3")).toBe("https://kestra.io/docs/migration-guide/v2.0.0")
        expect(hrefFor("2.0.5")).toBe("https://kestra.io/docs/migration-guide/v2.0.0")
        expect(hrefFor("2.0.0-rc13")).toBe("https://kestra.io/docs/migration-guide/v2.0.0")
    })
})
