import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"

const {routeQuery, routerPush} = vi.hoisted(() => ({
    routeQuery: {} as Record<string, string>,
    routerPush: vi.fn(),
}))

vi.mock("vue-i18n", () => ({useI18n: () => ({t: (key: string) => key})}))

vi.mock("vue-router", () => ({
    useRoute: () => ({query: routeQuery, params: {namespace: "company.team", id: "my_flow"}}),
    useRouter: () => ({push: routerPush}),
}))

vi.mock("../../../../src/utils/toast", () => ({
    useToast: () => ({confirm: vi.fn(), deleted: vi.fn(), error: vi.fn()}),
}))

vi.mock("../../../../src/stores/flow", () => ({
    useFlowStore: () => ({deleteRevision: vi.fn()}),
}))

vi.mock("../../../../src/composables/useEditorBindings", () => ({
    useEditorBindings: () => ({}),
}))

vi.mock("@kestra-io/design-system", () => ({
    KsEditor: {name: "KsEditor", template: "<div />"},
}))

import Revisions from "../../../../src/components/layout/Revisions.vue"

const passthrough = {template: "<div><slot /></div>"}

const stubs = {
    KsSelect: passthrough,
    KsOption: {props: ["label", "value"], template: "<div><slot /></div>"},
    KsTag: passthrough,
    KsButton: passthrough,
    KsButtonGroup: passthrough,
    KsNoData: {template: "<div class='no-data' />"},
}

// The template reads `$t` off the app instance, which mocking useI18n does not provide.
const mocks = {$t: (key: string) => key}

function revisionsUpTo(...numbers: number[]) {
    return numbers.map((revision) => ({revision, source: `source ${revision}`}))
}

function mountRevisions(revisions: {revision: number; source: string}[]) {
    return mount(Revisions, {
        props: {
            lang: "yaml",
            revisions,
            revisionSource: async (revision: number) => `source ${revision}`,
        },
        slots: {
            // The crud slot is where the crash surfaced: it is rendered with the
            // revision looked up through the stored index.
            crud: "<span class=\"crud-revision\">{{ params.revision }}</span>",
        },
        global: {stubs, mocks},
    })
}

// Left column renders before the right one, so this reads [left, right].
function shownRevisions(wrapper: ReturnType<typeof mountRevisions>) {
    return wrapper.findAll(".crud-revision").map((el) => el.text())
}

describe("Revisions — the list changing under the diff", () => {
    beforeEach(() => {
        for (const key of Object.keys(routeQuery)) delete routeQuery[key]
        routerPush.mockClear()
    })

    it("opens on the newest revision against the one before it", async () => {
        const wrapper = mountRevisions(revisionsUpTo(1, 2, 3, 4))
        await flushPromises()

        expect(shownRevisions(wrapper)).toEqual(["3", "4"])
    })

    it("opens on whatever the URL asks for", async () => {
        routeQuery.revisionLeft = "2"
        routeQuery.revisionRight = "4"

        const wrapper = mountRevisions(revisionsUpTo(1, 2, 3, 4))
        await flushPromises()

        expect(shownRevisions(wrapper)).toEqual(["2", "4"])
    })

    it("keeps showing the same revisions when an unrelated one is removed", async () => {
        const wrapper = mountRevisions(revisionsUpTo(1, 2, 3, 4))
        await flushPromises()

        // Revision 2 is neither side of the diff. Removing it shortens the list,
        // which used to leave the right index pointing past the end — the read
        // threw during render and the view went blank.
        await wrapper.setProps({revisions: revisionsUpTo(1, 3, 4)})
        await flushPromises()

        expect(wrapper.find(".revision").exists()).toBe(true)
        expect(shownRevisions(wrapper)).toEqual(["3", "4"])
    })

    it("holds position rather than jumping to the newest", async () => {
        // The reporter's steps start by picking revisions from the dropdowns,
        // which writes both into the URL. With more than four revisions, resetting
        // to the end of the list is distinguishable from stepping to a neighbour.
        routeQuery.revisionLeft = "3"
        routeQuery.revisionRight = "5"

        const wrapper = mountRevisions(revisionsUpTo(1, 2, 3, 4, 5, 6, 7, 8))
        await flushPromises()
        expect(shownRevisions(wrapper)).toEqual(["3", "5"])

        await wrapper.setProps({revisions: revisionsUpTo(1, 2, 3, 4, 6, 7, 8)})
        await flushPromises()

        // 6 took 5's place. Landing on 8 would drag the diff to the far end of a
        // list the user was reading the middle of.
        expect(shownRevisions(wrapper)).toEqual(["3", "6"])
    })

    it("falls back to a neighbour when the revision on show is the one removed", async () => {
        const wrapper = mountRevisions(revisionsUpTo(1, 2, 3, 4))
        await flushPromises()

        // 3 is what the left side is displaying.
        await wrapper.setProps({revisions: revisionsUpTo(1, 2, 4)})
        await flushPromises()

        expect(wrapper.find(".revision").exists()).toBe(true)
        expect(shownRevisions(wrapper)).toEqual(["2", "4"])
    })

    it("survives losing the newest revision", async () => {
        const wrapper = mountRevisions(revisionsUpTo(1, 2, 3, 4))
        await flushPromises()

        // Deleting the current revision is blocked in the UI, but the list can
        // still lose it — a restore renumbers, and the parent refetches.
        await wrapper.setProps({revisions: revisionsUpTo(1, 2, 3)})
        await flushPromises()

        expect(wrapper.find(".revision").exists()).toBe(true)
        expect(shownRevisions(wrapper)).toEqual(["2", "3"])
    })

    it("advances to a newly saved revision", async () => {
        // The same watcher fires when the list GROWS. Saving must still move the
        // right side onto the new newest revision.
        const wrapper = mountRevisions(revisionsUpTo(1, 2, 3, 4))
        await flushPromises()

        await wrapper.setProps({revisions: revisionsUpTo(1, 2, 3, 4, 5)})
        await flushPromises()

        expect(shownRevisions(wrapper)).toEqual(["3", "5"])
    })

    it("renders the empty state rather than a diff once one revision is left", async () => {
        const wrapper = mountRevisions(revisionsUpTo(1, 2))
        await flushPromises()

        await wrapper.setProps({revisions: revisionsUpTo(2)})
        await flushPromises()

        expect(wrapper.find(".revision").exists()).toBe(false)
        expect(wrapper.find(".no-data").exists()).toBe(true)
    })
})
