import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createApp, defineComponent, h} from "vue"
import {createPinia, setActivePinia, type Pinia} from "pinia"
import {createMemoryHistory, createRouter, type Router} from "vue-router"

vi.mock("vue-i18n", async (importOriginal) => {
    const actual = await importOriginal<typeof import("vue-i18n")>()
    return {
        ...actual,
        useI18n: () => ({t: (key: string) => key}),
    }
})

import {Comparators} from "@kestra-io/design-system"
import {useGanttExecutionFilter} from "./ganttExecutionFilter"
import {useExecutionsStore, type Execution} from "../../../stores/executions"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import {storageKeys} from "../../../utils/constants"

let pinia: Pinia
let router: Router
let store: ReturnType<typeof useExecutionsStore>

function setup<T>(useComposable: () => T): T {
    let api!: T
    const Comp = defineComponent({
        setup() {
            api = useComposable()
            return () => h("div")
        },
    })
    i18nMount(Comp, {global: {plugins: [pinia, router]}})
    return api
}

function keyByName(config: ReturnType<typeof useGanttExecutionFilter>, name: string) {
    return config.value.keys.find((key) => key.key === name)!
}

beforeEach(() => {
    pinia = createPinia()
    router = createRouter({
        history: createMemoryHistory(),
        routes: [{path: "/", component: {template: "<div />"}}],
    })
    // The store reads the route at setup, so it is created under an app that carries the router.
    createApp({}).use(pinia).use(router)
    setActivePinia(pinia)
    store = useExecutionsStore(pinia)
    localStorage.clear()
})

afterEach(() => {
    localStorage.clear()
})

describe("useGanttExecutionFilter", () => {
    it("declares level, state and task keys in order", () => {
        const config = setup(() => useGanttExecutionFilter())

        expect(config.value.keys.map((key) => key.key)).toEqual(["level", "state", "task"])
    })

    it("shows the level key by default with at-or-above and at-or-below comparators", () => {
        const config = setup(() => useGanttExecutionFilter())
        const level = keyByName(config, "level")

        expect(level.visibleByDefault).toBe(true)
        expect(level.comparators).toEqual([Comparators.GREATER_THAN_OR_EQUAL_TO, Comparators.LESS_THAN_OR_EQUAL_TO])
        expect(level.comparatorLabels).toEqual({
            [Comparators.GREATER_THAN_OR_EQUAL_TO]: "At or Above",
            [Comparators.LESS_THAN_OR_EQUAL_TO]: "At or Below",
        })
    })

    it("reads the level default from the stored log level and falls back to INFO", () => {
        const config = setup(() => useGanttExecutionFilter())
        const level = keyByName(config, "level")

        expect(typeof level.defaultValue).toBe("function")
        const defaultValue = level.defaultValue as () => string
        expect(defaultValue()).toBe("INFO")

        localStorage.setItem(storageKeys.DEFAULT_LOG_LEVEL, "DEBUG")
        expect(defaultValue()).toBe("DEBUG")
    })

    it("declares the state key as a coloured multi-select with in and not-in comparators", () => {
        const config = setup(() => useGanttExecutionFilter())
        const state = keyByName(config, "state")

        expect(state.valueType).toBe("multi-select")
        expect(state.comparators).toEqual([Comparators.IN, Comparators.NOT_IN])
        expect(state.colored).toBe(true)
    })

    it("offers the log levels as the level values", async () => {
        const config = setup(() => useGanttExecutionFilter())
        const level = keyByName(config, "level")

        const values = await level.valueProvider!()
        expect(values.map((option) => option.value)).toEqual(["TRACE", "DEBUG", "INFO", "WARN", "ERROR"])
    })

    it("maps each task run to its id, labelling a looped run with its value", async () => {
        store.execution = {
            id: "execution",
            taskRunList: [
                {id: "tr-1", taskId: "task-a"},
                {id: "tr-2", taskId: "task-b", value: "loop-1"},
            ],
        } as Execution

        const config = setup(() => useGanttExecutionFilter())
        const task = keyByName(config, "task")

        expect(await task.valueProvider!()).toEqual([
            {label: "task-a", value: "tr-1"},
            {label: "task-b - loop-1", value: "tr-2"},
        ])
    })

    it("yields an empty option list when no execution is in the store", async () => {
        const config = setup(() => useGanttExecutionFilter())
        const task = keyByName(config, "task")

        expect(await task.valueProvider!()).toEqual([])
    })
})
