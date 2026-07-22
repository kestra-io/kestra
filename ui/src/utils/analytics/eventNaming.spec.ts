import {describe, expect, it} from "vitest"
import {resolvePosthogEventName} from "./eventNaming"

const fixtures: [string, Record<string, any>, string][] = [
    ["flow_execution", {}, "app.flow.executed"],
    ["ai_copilot", {}, "app.ai-copilot.invoked"],
    ["blueprint", {}, "app.blueprint.used"],
    ["survey_submitted", {}, "app.survey.submitted"],
    ["survey_skipped", {}, "app.survey.skipped"],
    ["setup_flow:account_created", {}, "app.account.created"],
    ["setup_flow:account_creation_failed", {}, "app.account.creation-failed"],
    ["setup_flow:marketing_survey_submitted", {}, "app.marketing-survey.submitted"],
    ["setup_flow:marketing_survey_skipped", {}, "app.marketing-survey.skipped"],
    ["setup_flow:completed", {}, "app.setup-flow.completed"],
    ["error", {}, "app.error.occurred"],
    ["editor_tab_action", {action: "open"}, "app.editor-tab.opened"],
    ["editor_tab_action", {action: "close"}, "app.editor-tab.closed"],
    ["editor_tab_action", {action: "plugin_doc"}, "app.plugin-doc.viewed"],
    ["editor_tab_action", {action: "files_open"}, "app.editor-files.opened"],
    ["editor_tab_action", {action: "blueprint_selection"}, "app.editor-blueprint.selected"],
    ["ossauth", {action: "forgot_password_click"}, "app.forgot-password.clicked"],
    ["ossauth", {}, "app.oss-auth.completed"],
    ["onboarding", {onboarding: {action: "step_viewed"}}, "app.onboarding-step.viewed"],
    ["onboarding", {onboarding: {action: "step_next_clicked"}}, "app.onboarding-step.advanced"],
    ["onboarding", {onboarding: {action: "step_auto_advanced"}}, "app.onboarding-step.auto-advanced"],
    ["onboarding", {onboarding: {action: "step_validation_failed"}}, "app.onboarding-step.validation-failed"],
    ["onboarding", {onboarding: {action: "tutorial_completed"}}, "app.onboarding.completed"],
    ["onboarding", {onboarding: {action: "finish_explore_blueprints_clicked"}}, "app.onboarding.completed"],
    ["onboarding", {onboarding: {action: "finish_create_flow_clicked"}}, "app.onboarding.completed"],
    ["onboarding", {onboarding: {action: "tutorial_canceled"}}, "app.onboarding.cancelled"],
    ["onboarding", {onboarding: {action: "flow_saved_during_tutorial"}}, "app.onboarding-step.viewed"],
    ["onboarding", {onboarding: {action: "flow_executed_during_tutorial"}}, "app.onboarding-step.viewed"],
]

describe("resolvePosthogEventName", () => {
    it.each(fixtures)("resolves %s with %j to %s", (type, properties, expected) => {
        expect(resolvePosthogEventName(type, properties)).toEqual(expected)
    })

    it("falls back to the lowercased type for an unmapped event", () => {
        expect(resolvePosthogEventName("SOMETHING_NEW", {})).toEqual("something_new")
    })

    it("falls back to the base lowercased name when editor_tab_action has an unknown action", () => {
        expect(resolvePosthogEventName("editor_tab_action", {action: "unknown_action"})).toEqual("editor_tab_action")
    })

    it("falls back to app.oss-auth.completed when ossauth has an unknown action", () => {
        expect(resolvePosthogEventName("ossauth", {action: "unknown_action"})).toEqual("app.oss-auth.completed")
    })

    it("falls back to onboarding when the nested onboarding.action is unknown", () => {
        expect(resolvePosthogEventName("onboarding", {onboarding: {action: "unknown_action"}})).toEqual("onboarding")
    })

    it("reads the discriminator from the nested onboarding.action, not a top-level action", () => {
        expect(resolvePosthogEventName("onboarding", {action: "step_viewed"})).toEqual("onboarding")
    })

    it("resolves every mapped fixture to a surface.object.action shaped name", () => {
        fixtures.forEach(([type, properties]) => {
            expect(resolvePosthogEventName(type, properties)).toMatch(/^app\.[a-z0-9-]+\.[a-z0-9-]+$/)
        })
    })
})
