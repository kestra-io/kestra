import {describe, expect, test} from "vitest"

import {
    matchesExplorerItem,
    serializeExplorerValue,
    taskOutputLabel,
} from "../../../../../src/components/executions/outputs/explorerSearch"
import type {ExplorerItem} from "../../../../../src/components/executions/outputs/SidebarList.vue"

function explorerItem(overrides: Partial<ExplorerItem> = {}): ExplorerItem {
    return {
        label: "http_request",
        value: {code: 200, body: "healthy"},
        type: "object",
        preview: "{ code, body }",
        expression: "outputs.http_request",
        taskRunId: "task-run-1",
        ...overrides,
    }
}

describe("matchesExplorerItem", () => {
    test("matches labels case-insensitively", () => {
        expect(matchesExplorerItem(explorerItem(), "HTTP")).toBe(true)
    })

    test("matches serialized output keys", () => {
        expect(matchesExplorerItem(explorerItem(), "code")).toBe(true)
    })

    test("matches serialized output values", () => {
        expect(matchesExplorerItem(explorerItem(), "200")).toBe(true)
    })

    test("matches nested serialized output values", () => {
        expect(matchesExplorerItem(explorerItem({value: {response: {status: "SUCCESS"}}}), "success")).toBe(true)
    })

    test("matches additional searchable text", () => {
        expect(matchesExplorerItem(explorerItem({searchText: "iteration-42"}), "ITERATION")).toBe(true)
    })

    test("does not match absent values", () => {
        expect(matchesExplorerItem(explorerItem(), "missing")).toBe(false)
    })

    test("does not match unloaded outputs by key or value", () => {
        expect(matchesExplorerItem(explorerItem({value: undefined}), "code")).toBe(false)
    })
})

describe("serializeExplorerValue", () => {
    test("serializes nested objects", () => {
        expect(serializeExplorerValue({response: {code: 200}})).toBe("{\"response\":{\"code\":200}}")
    })

    test("serializes null and undefined explicitly", () => {
        expect(serializeExplorerValue(null)).toBe("null")
        expect(serializeExplorerValue(undefined)).toBe("")
    })
})

describe("taskOutputLabel", () => {
    test("appends iteration value when present", () => {
        expect(taskOutputLabel("each_task", "item-1")).toBe("each_task - item-1")
    })

    test("uses task id when iteration value is absent", () => {
        expect(taskOutputLabel("http_request", null)).toBe("http_request")
    })
})
