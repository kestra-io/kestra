import {describe, it, expect, vi, afterEach} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import en from "../../translations/en.json"

vi.mock("../filter/composables/useStateFilter", () => ({
    useStateFilter: () => ({navigateToStateFilter: vi.fn()}),
}))

vi.mock("../../stores/plugins", () => ({
    usePluginsStore: () => ({}),
}))

import TaskRunLine from "./TaskRunLine.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

let wrapper: VueWrapper | undefined

const stubs = {
    KsIcon: {template: "<span><slot /></span>"},
    KsTooltip: {template: "<div><slot /><slot name=\"content\" /></div>"},
    KsSelect: {name: "KsSelect", template: "<div class=\"ks-select-stub\"><slot /></div>"},
    KsOption: {name: "KsOption", template: "<div class=\"ks-option-stub\" />"},
    KsExecutionStatus: {template: "<div class=\"ks-status-stub\" />"},
    TaskIcon: {template: "<div class=\"task-icon-stub\" />"},
    TaskRunActions: {template: "<div class=\"task-run-actions-stub\" />"},
    Duration: {template: "<div class=\"duration-stub\" />"},
    Logs: {template: "<div class=\"logs-stub\" />"},
    Subflow: {template: "<div class=\"subflow-stub\" />"},
    ChevronRight: {template: "<div />"},
    ChevronDown: {template: "<div />"},
    Clock: {template: "<div />"},
}

const mockTaskRun = {
    id: "tr-1",
    taskId: "my-task",
    state: {
        current: "SUCCESS",
        duration: "PT1.5S",
        histories: [],
    },
    attempts: [
        {
            state: {
                current: "SUCCESS",
                duration: "PT1.5S",
                histories: [],
            },
        },
    ],
}

const mockExecution = {
    id: "exec-1",
    state: {current: "SUCCESS"},
    taskRunList: [mockTaskRun],
}

import type {Execution} from "../../stores/executions"

const mountTaskRunLine = () => {
    wrapper = i18nMount(TaskRunLine, {
        locales: en,
        props: {
            currentTaskRun: mockTaskRun,
            followedExecution: mockExecution as unknown as Execution,
        },
        global: {stubs},
    })
    return wrapper
}

describe("TaskRunLine.vue", () => {
    afterEach(() => {
        wrapper?.unmount()
    })

    it("renders design system classes and no Bootstrap classes", () => {
        const w = mountTaskRunLine()

        const taskIcon = w.find(".task-icon")
        expect(taskIcon.exists()).toBe(true)
        expect(taskIcon.classes()).not.toContain("d-none")
        expect(taskIcon.classes()).not.toContain("d-md-inline-block")

        const taskId = w.find(".task-id")
        expect(taskId.exists()).toBe(true)
        expect(taskId.classes()).not.toContain("flex-grow-1")

        const taskIdText = w.find(".task-id-text")
        expect(taskIdText.exists()).toBe(true)
        expect(taskIdText.classes()).not.toContain("me-1")
        expect(taskIdText.classes()).not.toContain("fw-bold")
        expect(taskIdText.text()).toBe("my-task")

        const taskDurations = w.findAll(".task-duration")
        expect(taskDurations.length).toBeGreaterThan(0)
        for (const td of taskDurations) {
            expect(td.classes()).not.toContain("d-none")
            expect(td.classes()).not.toContain("d-md-inline-block")
            const small = td.find("small")
            if (small.exists()) {
                expect(small.classes()).not.toContain("me-1")
            }
        }

        const attemptSelect = w.find(".attempt-select")
        expect(attemptSelect.exists()).toBe(true)
        expect(attemptSelect.classes()).not.toContain("d-none")
        expect(attemptSelect.classes()).not.toContain("d-md-inline-block")
    })
})
