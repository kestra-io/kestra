import {describe, it, expect} from "vitest"
import {buildSectionLanes, type SectionLaneSources} from "./blockSectionLanes"

// Prefixing the key proves every label comes from the translator and is not hardcoded.
const t = (key: string) => `translated:${key}`

const sources: SectionLaneSources = {
    triggers: [{id: "trigger-1"}],
    tasks: [{id: "task-1"}, {id: "task-2"}],
    errors: [{id: "error-1"}],
    finally: [{id: "finally-1"}],
    afterExecution: [{id: "after-1"}],
}

const buildLanes = (playgroundEnabled = true) => buildSectionLanes(t, sources, playgroundEnabled)
const laneOf = (section: string, playgroundEnabled = true) =>
    buildLanes(playgroundEnabled).find((lane) => lane.section === section)!

describe("buildSectionLanes", () => {
    it("returns the five lanes in order", () => {
        expect(buildLanes().map((lane) => lane.section)).toEqual(
            ["triggers", "tasks", "errors", "finally", "afterExecution"],
        )
    })

    it.each([
        ["triggers", "no_code.sections.triggers", "block_editor.add_trigger", "block_editor.trigger_noun"],
        ["tasks", "no_code.sections.tasks", "block_editor.add_task", "block_editor.task_noun"],
        ["errors", "block_editor.lane_errors", "block_editor.add_task", "block_editor.task_noun"],
        ["finally", "block_editor.lane_finally", "block_editor.add_task", "block_editor.task_noun"],
        ["afterExecution", "no_code.sections.afterExecution", "block_editor.add_task", "block_editor.task_noun"],
    ])("gives the %s lane a translated title, add label and empty label, and an icon", (section, title, addLabel, emptyLabel) => {
        const lane = laneOf(section)

        expect(lane.title).toBe(`translated:${title}`)
        expect(lane.addLabel).toBe(`translated:${addLabel}`)
        expect(lane.emptyLabel).toBe(`translated:${emptyLabel}`)
        expect(lane.icon).toBeDefined()
    })

    it("gives every lane its own icon", () => {
        expect(new Set(buildLanes().map((lane) => lane.icon)).size).toBe(5)
    })

    it("takes each lane's blocks from the matching key of the sources", () => {
        for (const lane of buildLanes()) {
            expect(lane.blocks).toBe(sources[lane.section])
        }
    })

    it.each([true, false])("never enables the playground on the triggers lane when playgroundEnabled is %s", (playgroundEnabled) => {
        expect(laneOf("triggers", playgroundEnabled).playgroundEnabled).toBe(false)
    })

    it.each([true, false])("follows playgroundEnabled=%s on the tasks, errors, finally and afterExecution lanes", (playgroundEnabled) => {
        for (const section of ["tasks", "errors", "finally", "afterExecution"]) {
            expect(laneOf(section, playgroundEnabled).playgroundEnabled).toBe(playgroundEnabled)
        }
    })

    it("sets supportsFlowable on tasks, errors, finally and afterExecution but not on triggers", () => {
        expect(laneOf("triggers").supportsFlowable).toBeFalsy()
        for (const section of ["tasks", "errors", "finally", "afterExecution"]) {
            expect(laneOf(section).supportsFlowable).toBe(true)
        }
    })

    it("gives errors the error tone and finally the warning tone, and no tone to the rest", () => {
        expect(laneOf("errors").tone).toBe("error")
        expect(laneOf("finally").tone).toBe("warning")
        for (const section of ["triggers", "tasks", "afterExecution"]) {
            expect(laneOf(section).tone).toBeUndefined()
        }
    })
})