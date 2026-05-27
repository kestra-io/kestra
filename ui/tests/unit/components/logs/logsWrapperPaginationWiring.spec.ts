import {describe, test, expect} from "vitest"
import {defineComponent, computed, watch, reactive, ref} from "vue"
import {mount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import KsDataTable from "@kestra-io/design-system/components/Data/KsDataTable/KsDataTable.vue"

const globalConfig = {
    plugins: [createI18n({legacy: false, locale: "en"}), KestraDesignSystem],
}

function makeHarness(initialQuery: Record<string, string> = {}) {
    const routeQuery = reactive<Record<string, string>>({...initialQuery})
    const filterResets = {count: 0}
    const loadCalls: Array<{page: number; size: number}> = []

    const Harness = defineComponent({
        components: {KsDataTable},
        setup() {
            const dataTable = ref<any>(null)
            const setDataTable = (el: any) => { dataTable.value = el }

            const loadData = async ({page, size}: {page: number; size: number}) => {
                loadCalls.push({page, size})
            }

            const urlPage = computed(() => Number(routeQuery.page) || 1)
            const urlSize = computed(() => Number(routeQuery.size) || 25)

            const filterQueryKey = computed(() => {
                const {page: _p, size: _s, sort: _so, ...filters} = routeQuery
                return JSON.stringify(filters)
            })
            watch(filterQueryKey, () => {
                filterResets.count += 1
                dataTable.value?.resetAndReload()
            })

            const onPageChanged = ({page, size}: {page: number; size: number}) => {
                routeQuery.page = String(page)
                routeQuery.size = String(size)
            }

            return {setDataTable, loadData, urlPage, urlSize, onPageChanged}
        },
        template: `
            <KsDataTable
                :ref="setDataTable"
                :loadData="loadData"
                :currentPage="urlPage"
                :pageSize="urlSize"
                :total="100"
                @page-changed="onPageChanged"
            />
        `,
    })

    return {Harness, routeQuery, filterResets, loadCalls}
}

describe("LogsWrapper-style pagination wiring", () => {
    test("mount with URL page=2 → loadData receives page 2 (not 1)", async () => {
        const {Harness, loadCalls} = makeHarness({
            page: "2",
            size: "25",
            "filters[level][EQUALS]": "INFO",
        })

        mount(Harness, {global: globalConfig})
        await flushPromises()

        expect(loadCalls.length).toBeGreaterThan(0)
        expect(loadCalls[0]).toEqual({page: 2, size: 25})
    })

    test("page-only URL change does NOT trigger the filter-change reset", async () => {
        const {Harness, routeQuery, filterResets} = makeHarness({
            page: "1",
            size: "25",
            "filters[level][EQUALS]": "INFO",
        })

        mount(Harness, {global: globalConfig})
        await flushPromises()
        const baseline = filterResets.count

        routeQuery.page = "5"
        routeQuery.size = "25"
        await flushPromises()

        expect(filterResets.count).toBe(baseline)
    })

    test("real filter change still triggers exactly one reset", async () => {
        const {Harness, routeQuery, filterResets} = makeHarness({
            "filters[level][EQUALS]": "INFO",
        })

        mount(Harness, {global: globalConfig})
        await flushPromises()
        const baseline = filterResets.count

        routeQuery["filters[level][EQUALS]"] = "DEBUG"
        await flushPromises()

        expect(filterResets.count).toBe(baseline + 1)
    })

    test("filter change while on page > 1 ends on page 1 with a fresh fetch", async () => {
        const {Harness, routeQuery, loadCalls} = makeHarness({
            page: "3",
            size: "25",
            "filters[level][EQUALS]": "INFO",
        })

        mount(Harness, {global: globalConfig})
        await flushPromises()
        loadCalls.length = 0

        routeQuery["filters[level][EQUALS]"] = "DEBUG"
        await flushPromises()

        expect(loadCalls.length).toBeGreaterThan(0)
        expect(loadCalls.every((c) => c.page === 1)).toBe(true)
        expect(loadCalls[loadCalls.length - 1]).toEqual({page: 1, size: 25})
    })
})
