import {describe, it, expect, vi, afterEach} from "vitest"
import {mount, DOMWrapper} from "@vue/test-utils"
import {createI18n} from "vue-i18n"

import BlockCommandMenu, {type BlockCommandMenuItem} from "../../../../../src/components/no-code/blocks/BlockCommandMenu.vue"

const messages = {
    en: {
        block_editor: {
            command_menu: {
                no_match: "No match",
                search_placeholder: "Type a command or search a task…",
                title: "Command menu",
            },
        },
    },
}

function makeItems(run: (id: string) => void): BlockCommandMenuItem[] {
    return [
        {id: "insert", group: "Insert", title: "Insert task after Log", run: () => run("insert")},
        {id: "open", group: "This block", title: "Open Log", run: () => run("open")},
        {id: "duplicate", group: "This block", title: "Duplicate Log", run: () => run("duplicate")},
    ]
}

// BlockCommandMenu teleports its content to document.body, so assertions
// query the real DOM (via DOMWrapper) instead of the component's own subtree.
function mountMenu(items: BlockCommandMenuItem[], contextLabel?: string) {
    const wrapper = mount(BlockCommandMenu, {
        attachTo: document.body,
        props: {items, contextLabel},
        global: {
            plugins: [createI18n({legacy: false, locale: "en", messages})],
            stubs: {
                KsInput: {
                    template: "<input :value='modelValue' @input=\"$emit('update:modelValue', $event.target.value)\" />",
                    props: ["modelValue"],
                    emits: ["update:modelValue"],
                    methods: {focus: () => undefined},
                },
            },
        },
    })
    const body = new DOMWrapper(document.body)
    return {wrapper, body}
}

describe("BlockCommandMenu", () => {
    afterEach(() => {
        document.body.innerHTML = ""
    })

    it("renders every item grouped", () => {
        // Given
        const items = makeItems(vi.fn())

        // When
        const {body} = mountMenu(items)

        // Then
        expect(body.findAll("[data-test='block-command-menu-item']")).toHaveLength(3)
    })

    it("filters items by typed text", async () => {
        // Given
        const items = makeItems(vi.fn())
        const {body} = mountMenu(items)

        // When
        await body.find("input").setValue("duplicate")

        // Then
        const rendered = body.findAll("[data-test='block-command-menu-item']")
        expect(rendered).toHaveLength(1)
        expect(rendered[0].text()).toContain("Duplicate Log")
    })

    it("shows an empty state when nothing matches", async () => {
        // Given
        const items = makeItems(vi.fn())
        const {body} = mountMenu(items)

        // When
        await body.find("input").setValue("nothing-matches-this")

        // Then
        expect(body.findAll("[data-test='block-command-menu-item']")).toHaveLength(0)
        expect(body.text()).toContain("No match")
    })

    it("runs the active item on Enter", async () => {
        // Given
        const run = vi.fn()
        const items = makeItems(run)
        const {body} = mountMenu(items)

        // When
        await body.find(".block-command-menu").trigger("keydown", {key: "Enter"})

        // Then
        expect(run).toHaveBeenCalledWith("insert")
    })

    it("moves the active item down and runs it on Enter", async () => {
        // Given
        const run = vi.fn()
        const items = makeItems(run)
        const {body} = mountMenu(items)

        // When
        await body.find(".block-command-menu").trigger("keydown", {key: "ArrowDown"})
        await body.find(".block-command-menu").trigger("keydown", {key: "Enter"})

        // Then
        expect(run).toHaveBeenCalledWith("open")
    })

    it("runs an item when clicked", async () => {
        // Given
        const run = vi.fn()
        const items = makeItems(run)
        const {body} = mountMenu(items)

        // When
        await body.findAll("[data-test='block-command-menu-item']")[2].trigger("click")

        // Then
        expect(run).toHaveBeenCalledWith("duplicate")
    })

    it("emits close on Escape when the search is empty", async () => {
        // Given
        const items = makeItems(vi.fn())
        const {wrapper, body} = mountMenu(items)

        // When
        await body.find(".block-command-menu").trigger("keydown", {key: "Escape"})

        // Then
        expect(wrapper.emitted("close")).toBeTruthy()
    })

    it("clears the search on Escape instead of closing when there is a query", async () => {
        // Given
        const items = makeItems(vi.fn())
        const {wrapper, body} = mountMenu(items)
        await body.find("input").setValue("duplicate")

        // When
        await body.find(".block-command-menu").trigger("keydown", {key: "Escape"})

        // Then
        expect(wrapper.emitted("close")).toBeFalsy()
        expect(body.findAll("[data-test='block-command-menu-item']")).toHaveLength(3)
    })

    it("emits close when clicking the overlay backdrop", async () => {
        // Given
        const items = makeItems(vi.fn())
        const {wrapper, body} = mountMenu(items)

        // When
        await body.find("[data-test='block-command-menu']").trigger("click")

        // Then
        expect(wrapper.emitted("close")).toBeTruthy()
    })

    it("hides the search-only items until something is typed", async () => {
        // Given — task types are search-only so they do not flood the idle list
        const items: BlockCommandMenuItem[] = [
            ...makeItems(vi.fn()),
            {id: "type-log", group: "Task types", title: "Log", subtitle: "io.kestra.plugin.core.log.Log", searchOnly: true, run: vi.fn()},
        ]
        const {body} = mountMenu(items)

        // When
        const idle = body.findAll("[data-test='block-command-menu-item']")

        // Then
        expect(idle).toHaveLength(3)
        expect(body.text()).not.toContain("io.kestra.plugin.core.log.Log")
    })

    it("surfaces a search-only item matched on its title", async () => {
        // Given
        const run = vi.fn()
        const items: BlockCommandMenuItem[] = [
            ...makeItems(vi.fn()),
            {id: "type-log", group: "Task types", title: "Log", subtitle: "io.kestra.plugin.core.log.Log", searchOnly: true, run},
        ]
        const {body} = mountMenu(items)

        // When
        await body.find("input").setValue("log")
        const rendered = body.findAll("[data-test='block-command-menu-item']")

        // Then
        expect(rendered.map(item => item.text())).toContain("Logio.kestra.plugin.core.log.Log")

        // And it runs when picked
        await rendered[rendered.length - 1].trigger("click")
        expect(run).toHaveBeenCalled()
    })

    it("shows the context label when provided", () => {
        // Given
        const items = makeItems(vi.fn())

        // When
        const {body} = mountMenu(items, "after Log")

        // Then
        expect(body.text()).toContain("after Log")
    })
})
