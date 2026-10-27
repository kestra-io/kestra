import {beforeEach, describe, expect, it, vi} from "vitest"
import ServerNetworkOutline from "vue-material-design-icons/ServerNetworkOutline.vue"

const {t} = vi.hoisted(() => ({
    t: vi.fn((key: string) => key),
}))

vi.mock("vue-i18n", () => ({
    useI18n: () => ({t}),
}))

import {useAdminTabs} from "./useAdminTabs"
import type {RouteTab} from "../stores/routeTabs"

function getTabs(): RouteTab[] {
    return useAdminTabs().adminTabs.value
}

describe("useAdminTabs", () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it("returns tabs in the documented order", () => {
        const tabs = getTabs()

        expect(tabs).toEqual([
            {
                title: "main_configuration",
                header: true,
            },
            {
                name: "preferences",
                title: "admin_preferences",
                route: {name: "preferences"},
            },
            {
                title: "instance",
                header: true,
            },
            {
                name: "instance-ee",
                title: "instance",
                icon: ServerNetworkOutline,
                locked: true,
                route: {name: "admin/instance"},
            },
        ])
    })

    it("marks the two header entries with header: true and no route", () => {
        const tabs = getTabs()
        const headers = tabs.filter(tab => tab.header)

        expect(headers).toHaveLength(2)
        for (const header of headers) {
            expect(header.header).toBe(true)
            expect(header.route).toBeUndefined()
        }
        expect(tabs[0].header).toBe(true)
        expect(tabs[0].route).toBeUndefined()
        expect(tabs[2].header).toBe(true)
        expect(tabs[2].route).toBeUndefined()
    })

    it("points the preferences tab at the preferences route", () => {
        const tabs = getTabs()
        const preferencesTab = tabs.find(tab => tab.name === "preferences")

        expect(preferencesTab).toBeDefined()
        expect(preferencesTab?.route).toEqual({name: "preferences"})
    })

    it("marks the Enterprise instance tab as locked", () => {
        const tabs = getTabs()
        const instanceTab = tabs.find(tab => tab.name === "instance-ee")

        expect(instanceTab).toBeDefined()
        expect(instanceTab?.locked).toBe(true)
    })

    it("ensures every non-header tab has both a name and a route", () => {
        const tabs = getTabs()
        const nonHeaders = tabs.filter(tab => !tab.header)

        expect(nonHeaders.length).toBeGreaterThan(0)
        for (const tab of nonHeaders) {
            expect(tab.name).toBeDefined()
            expect(tab.name).not.toBe("")
            expect(tab.route).toBeDefined()
        }
    })

    it("resolves tab titles through vue-i18n", () => {
        const tabs = getTabs()

        expect(tabs[0].title).toBe("main_configuration")
        expect(tabs[1].title).toBe("admin_preferences")
        expect(tabs[2].title).toBe("instance")
        expect(tabs[3].title).toBe("instance")

        expect(t).toHaveBeenCalledWith("main_configuration")
        expect(t).toHaveBeenCalledWith("admin_preferences")
        expect(t).toHaveBeenCalledWith("instance")
        expect(t).toHaveBeenCalledTimes(4)
    })
})
