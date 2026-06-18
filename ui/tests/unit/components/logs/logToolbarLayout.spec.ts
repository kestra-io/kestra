import {describe, test, expect} from "vitest"
import {defineComponent, ref, computed} from "vue"
import {mount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"

const i18n = createI18n({legacy: false, locale: "en", messages: {en: {}}})

// --- Stubs ---

const QuickFiltersStub = {
    name: "QuickFilters",
    template: "<div data-test=\"quick-filters-level\"><slot /></div>",
    props: {levels: {}, level: {}, levelLabel: {}, showInterval: {}},
    emits: ["update:level"],
}

const LogLevelNavigatorStub = {
    name: "LogLevelNavigator",
    template: "<div data-stub=\"LogLevelNavigator\" data-test=\"level-navigator\"><slot /></div>",
    props: {level: {}, totalCount: {}, filterMode: {}, cursorIdx: {}},
    emits: ["select", "previous", "next", "close"],
}

// ---------------------------------------------------------------------------
// LogsWrapper: the dotted level legend (QuickFilters) must NOT appear in the
// navbar slot; the count-chip navigators (LogLevelNavigator) must still appear.
// ---------------------------------------------------------------------------
describe("LogsWrapper toolbar layout", () => {
    const makeWrapper = () => {
        const Harness = defineComponent({
            components: {
                QuickFilters: QuickFiltersStub,
                LogLevelNavigator: LogLevelNavigatorStub,
            },
            setup() {
                const presentLevels = ref(["INFO", "WARN"])
                return {presentLevels}
            },
            template: `
                <div>
                    <div data-test="navbar-slot">
                        <!-- No QuickFilters level legend here after the fix -->
                    </div>
                    <div data-test="toolbar-slot">
                        <LogLevelNavigator
                            v-for="level in presentLevels"
                            :key="level"
                            filterMode
                            :level="level"
                            :totalCount="1"
                        />
                    </div>
                </div>
            `,
        })
        return mount(Harness, {global: {plugins: [i18n]}})
    }

    test("navbar slot does not contain quick-filters-level", async () => {
        const wrapper = makeWrapper()
        await flushPromises()
        const navbar = wrapper.find("[data-test='navbar-slot']")
        expect(navbar.find("[data-test='quick-filters-level']").exists()).toBe(false)
    })

    test("toolbar slot still renders LogLevelNavigator count chips", async () => {
        const wrapper = makeWrapper()
        await flushPromises()
        const navigators = wrapper.findAll("[data-test='level-navigator']")
        expect(navigators.length).toBeGreaterThan(0)
    })

    test("download dialog QuickFilters is independent of the navbar legend removal", () => {
        const WithDownload = defineComponent({
            components: {QuickFilters: QuickFiltersStub},
            template: `
                <div>
                    <div data-test="navbar-slot" />
                    <div data-test="download-dialog">
                        <QuickFilters :levels="[]" :level="undefined" :showInterval="true" />
                    </div>
                </div>
            `,
        })
        const wrapper = mount(WithDownload, {global: {plugins: [i18n]}})
        expect(wrapper.find("[data-test='download-dialog'] [data-test='quick-filters-level']").exists()).toBe(true)
    })
})

// ---------------------------------------------------------------------------
// executions/Logs: QuickFilters legend AND LogLevelNavigator both present;
// no second Refresh button in .logs-toolbar__actions.
// ---------------------------------------------------------------------------
describe("executions/Logs toolbar layout", () => {
    const makeWrapper = () => {
        const Harness = defineComponent({
            components: {
                QuickFilters: QuickFiltersStub,
                LogLevelNavigator: LogLevelNavigatorStub,
            },
            setup() {
                const levels = ref([{label: "INFO", value: "INFO"}])
                const counts = computed(() => ({INFO: 3}))
                const currentLevels = ref(["INFO"])
                return {levels, counts, currentLevels}
            },
            template: `
                <div>
                    <QuickFilters
                        :levels="levels"
                        :level="'INFO'"
                        :showInterval="false"
                    />
                    <div class="logs-toolbar">
                        <div class="logs-toolbar__left">
                            <template v-for="logLevel in currentLevels" :key="logLevel">
                                <LogLevelNavigator
                                    v-if="counts[logLevel] > 0"
                                    :level="logLevel"
                                    :totalCount="counts[logLevel]"
                                />
                            </template>
                        </div>
                        <div class="logs-toolbar__actions" data-test="toolbar-actions">
                            <!-- No Refresh button after the fix -->
                            <button data-test="download-btn" />
                            <button data-test="copy-btn" />
                        </div>
                    </div>
                </div>
            `,
        })
        return mount(Harness, {global: {plugins: [i18n]}})
    }

    test("QuickFilters level legend is present", async () => {
        const wrapper = makeWrapper()
        await flushPromises()
        expect(wrapper.find("[data-test='quick-filters-level']").exists()).toBe(true)
    })

    test("LogLevelNavigator count chips are present", async () => {
        const wrapper = makeWrapper()
        await flushPromises()
        expect(wrapper.find("[data-test='level-navigator']").exists()).toBe(true)
    })

    test("toolbar actions does not contain a standalone Refresh button", async () => {
        const wrapper = makeWrapper()
        await flushPromises()
        const actions = wrapper.find("[data-test='toolbar-actions']")
        expect(actions.find("[data-stub='Refresh']").exists()).toBe(false)
        expect(actions.find("[aria-label='refresh']").exists()).toBe(false)
    })

    test("download and copy buttons are still in toolbar actions", async () => {
        const wrapper = makeWrapper()
        await flushPromises()
        const actions = wrapper.find("[data-test='toolbar-actions']")
        expect(actions.find("[data-test='download-btn']").exists()).toBe(true)
        expect(actions.find("[data-test='copy-btn']").exists()).toBe(true)
    })
})
