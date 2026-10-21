import {describe, it, expect, vi, beforeEach} from "vitest"
import {i18nShallowMount} from "../../../../tests/unit/i18nMount"

const {setTabs, clearTabsIfOwner} = vi.hoisted(() => ({setTabs: vi.fn(), clearTabsIfOwner: vi.fn()}))

vi.mock("../../../stores/routeTabs", () => ({useRouteTabsStore: () => ({setTabs, clearTabsIfOwner})}))
vi.mock("../../../composables/useRouteContext", () => ({default: vi.fn()}))
// Only the tab registration is under test; the browser and the detail drag the whole blueprints
// dependency graph in, and some of it writes to localStorage on import.
vi.mock("./BlueprintsBrowser.vue", () => ({default: {name: "BlueprintsBrowser", template: "<div />"}}))
vi.mock("override/components/flows/blueprints/BlueprintDetail.vue", () => ({
    default: {name: "BlueprintDetail", template: "<div />"},
}))

import BlueprintsPage from "./BlueprintsPage.vue"

const tabs = [{name: "flow-community", title: "Flows"}]

beforeEach(() => {
    setTabs.mockClear()
    clearTabsIfOwner.mockClear()
})

describe("BlueprintsPage route tabs", () => {
    it("registers the tabs it is given and clears them on unmount", () => {
        const wrapper = i18nShallowMount(BlueprintsPage, {props: {kind: "flow", tabs}})

        expect(setTabs).toHaveBeenCalledWith(expect.objectContaining({tabs}))
        const {ownerId} = setTabs.mock.calls[0][0]

        wrapper.unmount()

        expect(clearTabsIfOwner).toHaveBeenCalledWith(ownerId)
    })

    it("re-registers when the tabs change", async () => {
        const wrapper = i18nShallowMount(BlueprintsPage, {props: {kind: "flow", tabs}})
        const next = [...tabs, {name: "custom", title: "Custom"}]

        await wrapper.setProps({tabs: next})

        expect(setTabs).toHaveBeenLastCalledWith(expect.objectContaining({tabs: next}))
    })

    it("leaves the route tabs alone when embedded", () => {
        i18nShallowMount(BlueprintsPage, {props: {kind: "flow", tabs, embed: true}})

        expect(setTabs).not.toHaveBeenCalled()
    })
})
