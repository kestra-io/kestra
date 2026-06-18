import {describe, test, expect, vi, beforeEach, afterEach} from "vitest"
import {createApp} from "vue"
import {createRouter, createMemoryHistory} from "vue-router"
import type {App} from "vue"
import type {AppliedFilter} from "../../../../src/components/Data/KsDataTable/filter/utils/filterTypes"
import {Comparators} from "../../../../src/components/Data/KsDataTable/filter/utils/filterTypes"

vi.mock("vue-router", async (importOriginal) => {
    const actual = await importOriginal<typeof import("vue-router")>()
    return {
        ...actual,
        useRoute: () => ({name: "test-route", path: "/test"}),
    }
})

const makeFilter = (overrides: Partial<AppliedFilter> = {}): AppliedFilter => ({
    id: "f1",
    key: "namespace",
    keyLabel: "Namespace",
    comparator: Comparators.EQUALS,
    comparatorLabel: "Equals",
    value: "io.kestra",
    valueLabel: "io.kestra",
    ...overrides,
})

let app: App
let useSavedFilters: typeof import("../../../../src/components/Data/KsDataTable/filter/composables/useSavedFilters").useSavedFilters

beforeEach(async () => {
    localStorage.clear()
    const mod = await import("../../../../src/components/Data/KsDataTable/filter/composables/useSavedFilters")
    useSavedFilters = mod.useSavedFilters

    const router = createRouter({
        history: createMemoryHistory(),
        routes: [{path: "/", component: {template: "<div/>"}}],
    })
    app = createApp({template: "<div/>"})
    app.use(router)
    app.mount(document.createElement("div"))
})

afterEach(() => {
    app.unmount()
    localStorage.clear()
})

describe("useSavedFilters", () => {
    test("saveFilter persists a new saved filter with generated id and createdAt", () => {
        // Given
        const {saveFilter, savedFilters} = useSavedFilters("test")
        const filters = [makeFilter()]

        // When
        saveFilter("My filter", "A description", filters)

        // Then
        expect(savedFilters.value).toHaveLength(1)
        const saved = savedFilters.value[0]
        expect(saved.name).toBe("My filter")
        expect(saved.description).toBe("A description")
        expect(saved.filters).toHaveLength(1)
        expect(saved.filters[0].key).toBe("namespace")
        expect(saved.id).toMatch(/^saved_/)
        expect(saved.createdAt).toBeInstanceOf(Date)
    })

    test("updateSavedFilter updates name, description and conditions while preserving id and createdAt", () => {
        // Given
        const {saveFilter, updateSavedFilter, savedFilters} = useSavedFilters("test")
        const originalFilter = makeFilter({id: "f1", key: "namespace", value: "io.kestra"})
        saveFilter("Original", "desc", [originalFilter])
        const savedId = savedFilters.value[0].id
        const savedCreatedAt = savedFilters.value[0].createdAt

        const updatedFilter = makeFilter({id: "f2", key: "flowId", value: "myFlow", keyLabel: "Flow ID", valueLabel: "myFlow"})

        // When
        updateSavedFilter(savedId, "Updated", "new desc", [updatedFilter])

        // Then
        expect(savedFilters.value).toHaveLength(1)
        const updated = savedFilters.value[0]
        expect(updated.id).toBe(savedId)
        expect(updated.createdAt).toEqual(savedCreatedAt)
        expect(updated.name).toBe("Updated")
        expect(updated.description).toBe("new desc")
        expect(updated.filters).toHaveLength(1)
        expect(updated.filters[0].key).toBe("flowId")
    })

    test("updateSavedFilter does nothing when id does not exist", () => {
        // Given
        const {saveFilter, updateSavedFilter, savedFilters} = useSavedFilters("test")
        saveFilter("Existing", "", [makeFilter()])

        // When
        updateSavedFilter("nonexistent-id", "New name", "", [])

        // Then
        expect(savedFilters.value[0].name).toBe("Existing")
    })

    test("deleteSavedFilter removes the filter by id", async () => {
        // Given
        const {saveFilter, deleteSavedFilter, savedFilters} = useSavedFilters("test")
        saveFilter("A", "", [makeFilter()])
        await new Promise((r) => setTimeout(r, 2))
        saveFilter("B", "", [makeFilter({id: "f2"})])

        expect(savedFilters.value).toHaveLength(2)
        const toDelete = savedFilters.value[0]

        // When
        deleteSavedFilter(toDelete)

        // Then
        expect(savedFilters.value).toHaveLength(1)
        expect(savedFilters.value[0].name).toBe("B")
    })

    test("date range value round-trips through storage serialization", () => {
        // Given
        const {saveFilter, savedFilters} = useSavedFilters("test")
        const start = new Date("2024-01-01T00:00:00.000Z")
        const end = new Date("2024-01-31T00:00:00.000Z")
        const rangeFilter = makeFilter({
            value: {startDate: start, endDate: end},
            valueLabel: "Jan 2024",
        })

        // When
        saveFilter("Range filter", "", [rangeFilter])

        // Then: read back from the reactive ref (which applies deserializeDates via the storage serializer)
        const saved = savedFilters.value[0]
        const value = saved.filters[0].value as {startDate: Date; endDate: Date}
        expect(value.startDate).toBeInstanceOf(Date)
        expect(value.endDate).toBeInstanceOf(Date)
        expect(value.startDate.toISOString()).toBe(start.toISOString())
        expect(value.endDate.toISOString()).toBe(end.toISOString())
    })

    test("ISO date string value round-trips through storage serialization as Date", () => {
        // Given
        const {saveFilter, savedFilters} = useSavedFilters("test")
        const isoDate = new Date("2024-06-15T12:00:00.000Z")
        const dateFilter = makeFilter({value: isoDate, valueLabel: "2024-06-15"})

        // When
        saveFilter("Date filter", "", [dateFilter])

        // Then
        const saved = savedFilters.value[0]
        const value = saved.filters[0].value
        expect(value).toBeInstanceOf(Date)
        expect((value as Date).toISOString()).toBe(isoDate.toISOString())
    })
})
