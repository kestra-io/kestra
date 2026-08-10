import {describe, it, expect} from "vitest"

import {isTaskListPath, sectionFromParentPath} from "../../../../../src/components/no-code/blocks/blockSections"

describe("blockSections", () => {
    describe("isTaskListPath", () => {
        it.each([
            "tasks",
            "triggers",
            "errors",
            "finally",
            "afterExecution",
        ])("recognises the root %s lane", (section) => {
            // Given

            // When
            const result = isTaskListPath(section)

            // Then
            expect(result).toBe(true)
        })

        it.each([
            "tasks[0].tasks",
            "tasks[0].then",
            "tasks[0].else",
            "tasks[0].errors",
            "tasks[0].finally",
            "tasks[0].defaults",
            "tasks[1].cases.prod",
            "tasks[1].cases[\"with space\"]",
        ])("recognises the nested %s lane", (path) => {
            // Given

            // When
            const result = isTaskListPath(path)

            // Then
            expect(result).toBe(true)
        })

        it.each([
            "inputs",
            "outputs",
            "variables",
            "labels",
            "tasks[0].inputs",
            "concurrency",
        ])("rejects %s, which a schema-driven form owns", (path) => {
            // Given

            // When
            const result = isTaskListPath(path)

            // Then
            expect(result).toBe(false)
        })
    })

    describe("sectionFromParentPath", () => {
        it("maps a lane to the section that owns it", () => {
            // Given

            // When / Then
            expect(sectionFromParentPath("errors")).toBe("errors")
            expect(sectionFromParentPath("tasks[0].finally")).toBe("finally")
            expect(sectionFromParentPath("triggers")).toBe("triggers")
            expect(sectionFromParentPath("tasks[0].then")).toBe("tasks")
        })
    })
})
