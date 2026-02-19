import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";

export interface OnboardingValidationResult {
    ok: boolean;
    level?: "info" | "warn" | "error";
    message?: string;
}

export interface OnboardingGuideContext {
    flowYaml?: string;
    routeName?: string | null;
    saveCount: number;
    executionCount: number;
}

export type OnboardingStepType =
    | "code_edit"
    | "action_save"
    | "action_execute"
    | "action_navigate"
    | "inspection"
    | "finish";

export interface OnboardingOverlayPosition {
    vertical: "top" | "middle" | "bottom";
    horizontal: "left" | "center" | "right";
}

export interface OnboardingGuideStep {
    id: string;
    stepType: OnboardingStepType;
    title: string;
    description: string;
    showCompletionBadge?: boolean;
    targetSelector?: string;
    overlayPosition?: OnboardingOverlayPosition;
    snippet?: string;
    snippetCopyEnabled?: boolean;
    actionNote?: string;
    validationVisibility?: "always" | "after_input";
    allowNextOnWarning?: boolean;
    shouldAutoAdvance?: (context: OnboardingGuideContext) => boolean;
    validate: (context: OnboardingGuideContext) => OnboardingValidationResult;
}

const parseFlow = (flowYaml = "") => {
    try {
        const parsed = YAML_UTILS.parse(flowYaml);
        return {parsed};
    } catch (error: any) {
        return {error: error?.message || "Invalid YAML"};
    }
};

export const FIRST_FLOW_GUIDE_STEPS: OnboardingGuideStep[] = [
    {
        id: "flow_basics",
        stepType: "inspection",
        title: "onboarding.steps.flow_basics.title",
        description: "onboarding.steps.flow_basics.description",
        showCompletionBadge: false,
        targetSelector: "#editorWrapper",
        snippet: `id: my_flow
namespace: company.team

inputs:
  - id: name
    type: STRING

tasks:
  - id: greet
    type: io.kestra.plugin.scripts.python.Script
    script: |
      print("Hello {{ inputs.name }}")`,
        snippetCopyEnabled: false,
        validate: () => ({ok: true}),
    },
    {
        id: "add_id",
        stepType: "code_edit",
        title: "onboarding.steps.add_id.title",
        description: "onboarding.steps.add_id.description",
        targetSelector: "#editorWrapper",
        snippet: "id: my_flow",
        validate: ({flowYaml}) => {
            const {parsed, error} = parseFlow(flowYaml);
            if (error) {
                return {ok: false, level: "info", message: "Please fix the YAML formatting, then continue."};
            }
            if (!parsed?.id) {
                return {ok: false, level: "info", message: "onboarding.validation.add_id"};
            }
            return {ok: true};
        },
    },
    {
        id: "add_namespace",
        stepType: "code_edit",
        title: "onboarding.steps.add_namespace.title",
        description: "onboarding.steps.add_namespace.description",
        targetSelector: "#editorWrapper",
        snippet: "namespace: company.team",
        validate: ({flowYaml}) => {
            const {parsed, error} = parseFlow(flowYaml);
            if (error) {
                return {ok: false, level: "info", message: "Please fix the YAML formatting, then continue."};
            }
            if (!parsed?.namespace) {
                return {ok: false, level: "info", message: "onboarding.validation.add_namespace"};
            }
            return {ok: true};
        },
    },
    {
        id: "add_input",
        stepType: "code_edit",
        title: "onboarding.steps.add_input.title",
        description: "onboarding.steps.add_input.description",
        targetSelector: "#editorWrapper",
        snippet: `inputs:
  - id: name
    type: STRING`,
        validate: ({flowYaml}) => {
            const {parsed, error} = parseFlow(flowYaml);
            if (error) {
                return {ok: false, level: "info", message: "Please fix the YAML formatting, then continue."};
            }
            if (!Array.isArray(parsed?.inputs) || parsed.inputs.length === 0) {
                return {ok: false, level: "info", message: "onboarding.validation.add_input_section"};
            }
            const nameInput = parsed.inputs.find((input: any) => input?.id === "name");
            if (!nameInput) {
                return {ok: false, level: "info", message: "onboarding.validation.add_input_id"};
            }
            if (nameInput?.type !== "STRING") {
                return {ok: false, level: "info", message: "onboarding.validation.add_input_type"};
            }
            return {ok: true};
        },
    },
    {
        id: "add_log_task",
        stepType: "code_edit",
        title: "onboarding.steps.add_log_task.title",
        description: "onboarding.steps.add_log_task.description",
        targetSelector: "#editorWrapper",
        snippet: `tasks:
  - id: greet
    type: io.kestra.plugin.scripts.python.Script
    script: |
      print("Hello {{ inputs.name }}")`,
        validate: ({flowYaml}) => {
            const {parsed, error} = parseFlow(flowYaml);
            if (error) {
                return {ok: false, level: "info", message: "Please fix the YAML formatting, then continue."};
            }
            const firstTask = parsed?.tasks?.[0];
            if (!Array.isArray(parsed?.tasks) || !parsed.tasks[0]) {
                return {ok: false, level: "info", message: "onboarding.validation.add_log_task_section"};
            }
            if (!firstTask?.id) {
                return {ok: false, level: "info", message: "onboarding.validation.add_log_task_id"};
            }
            if (firstTask?.type !== "io.kestra.plugin.scripts.python.Script") {
                return {ok: false, level: "info", message: "onboarding.validation.add_log_task_type"};
            }
            if (!firstTask?.script || typeof firstTask.script !== "string") {
                return {ok: false, level: "info", message: "onboarding.validation.add_log_task_message"};
            }
            if (!/\{\{\s*inputs\.name\s*}}/.test(firstTask.script)) {
                return {ok: false, level: "info", message: "onboarding.validation.add_log_task_pebble"};
            }
            return {ok: true};
        },
    },
    {
        id: "save_flow",
        stepType: "action_save",
        title: "onboarding.steps.save_flow.title",
        description: "onboarding.steps.save_flow.description",
        targetSelector: "[data-onboarding-target=\"flow-save-button\"], .edit-flow-save-button",
        actionNote: "onboarding.actions.save_to_continue",
        shouldAutoAdvance: ({saveCount}) => saveCount > 0,
        validate: ({saveCount}) => {
            if (saveCount < 1) {
                return {ok: false, level: "info", message: "onboarding.validation.save_flow"};
            }
            return {ok: true};
        },
    },
    {
        id: "execute_flow",
        stepType: "action_execute",
        title: "onboarding.steps.execute_flow.title",
        description: "onboarding.steps.execute_flow.description",
        overlayPosition: {vertical: "bottom", horizontal: "right"},
        targetSelector: "[data-onboarding-target=\"flow-execute-button\"], #execute-button",
        actionNote: "onboarding.actions.execute_to_continue",
        shouldAutoAdvance: ({executionCount}) => executionCount > 0,
        validate: ({executionCount}) => {
            if (executionCount < 1) {
                return {ok: false, level: "info", message: "onboarding.validation.execute_flow"};
            }
            return {ok: true};
        },
    },
    {
        id: "view_logs_status",
        stepType: "inspection",
        title: "onboarding.steps.view_logs_status.title",
        description: "onboarding.steps.view_logs_status.description",
        showCompletionBadge: false,
        overlayPosition: {vertical: "bottom", horizontal: "right"},
        targetSelector: "[data-onboarding-target=\"execution-gantt\"], #gantt",
        validate: ({routeName}) => {
            if (routeName !== "executions/update") {
                return {ok: false, level: "info", message: "onboarding.validation.view_logs_status"};
            }
            return {ok: true};
        },
    },
    {
        id: "edit_flow_from_execution",
        stepType: "action_navigate",
        title: "onboarding.steps.edit_flow_from_execution.title",
        description: "onboarding.steps.edit_flow_from_execution.description",
        overlayPosition: {vertical: "bottom", horizontal: "right"},
        targetSelector: "[data-onboarding-target=\"execution-edit-flow-button\"], .execution-edit-flow-button",
        actionNote: "onboarding.actions.edit_flow_to_continue",
        shouldAutoAdvance: ({routeName}) => routeName === "flows/update",
        validate: ({routeName}) => {
            if (routeName !== "flows/update") {
                return {ok: false, level: "info", message: "onboarding.validation.edit_flow_from_execution"};
            }
            return {ok: true};
        },
    },
    {
        id: "add_cron_trigger",
        stepType: "code_edit",
        title: "onboarding.steps.add_cron_trigger.title",
        description: "onboarding.steps.add_cron_trigger.description",
        overlayPosition: {vertical: "middle", horizontal: "right"},
        targetSelector: "#editorWrapper",
        snippet: `triggers:
  - id: every_5_minutes
    type: io.kestra.plugin.core.trigger.Schedule
    cron: "*/5 * * * *"`,
        validate: ({flowYaml}) => {
            const {parsed, error} = parseFlow(flowYaml);
            if (error) {
                return {ok: false, level: "info", message: "Please fix the YAML formatting, then continue."};
            }
            if (!Array.isArray(parsed?.triggers) || parsed.triggers.length === 0) {
                return {ok: false, level: "info", message: "onboarding.validation.add_cron_trigger_section"};
            }
            const scheduleTrigger = parsed.triggers.find(
                (trigger: any) => trigger?.type === "io.kestra.plugin.core.trigger.Schedule",
            );
            if (!scheduleTrigger) {
                return {ok: false, level: "info", message: "onboarding.validation.add_cron_trigger_type"};
            }
            if (!scheduleTrigger?.cron || typeof scheduleTrigger.cron !== "string") {
                return {ok: false, level: "info", message: "onboarding.validation.add_cron_trigger_cron"};
            }
            return {ok: true};
        },
    },
    {
        id: "add_input_default",
        stepType: "code_edit",
        title: "onboarding.steps.add_input_default.title",
        description: "onboarding.steps.add_input_default.description",
        overlayPosition: {vertical: "middle", horizontal: "right"},
        targetSelector: "#editorWrapper",
        snippet: `inputs:
  - id: name
    type: STRING
    defaults: "Kestra"`,
        validate: ({flowYaml}) => {
            const {parsed, error} = parseFlow(flowYaml);
            if (error) {
                return {ok: false, level: "info", message: "Please fix the YAML formatting, then continue."};
            }
            if (!Array.isArray(parsed?.inputs) || parsed.inputs.length === 0) {
                return {ok: false, level: "info", message: "onboarding.validation.add_input_default_section"};
            }
            const nameInputs = parsed.inputs.filter((input: any) => input?.id === "name");
            if (nameInputs.length > 1) {
                return {ok: false, level: "info", message: "onboarding.validation.add_input_default_section"};
            }
            const nameInput = nameInputs[0];
            if (!nameInput) {
                return {ok: false, level: "info", message: "onboarding.validation.add_input_default_id"};
            }
            if (nameInput.defaults === undefined || nameInput.defaults === null || nameInput.defaults === "") {
                return {ok: false, level: "info", message: "onboarding.validation.add_input_default_defaults"};
            }
            return {ok: true};
        },
    },
    {
        id: "save_flow_again",
        stepType: "action_save",
        title: "onboarding.steps.save_flow_again.title",
        description: "onboarding.steps.save_flow_again.description",
        overlayPosition: {vertical: "middle", horizontal: "right"},
        targetSelector: "[data-onboarding-target=\"flow-save-button\"], .edit-flow-save-button",
        actionNote: "onboarding.actions.save_to_continue",
        shouldAutoAdvance: ({saveCount}) => saveCount > 1,
        validate: ({saveCount}) => {
            if (saveCount < 2) {
                return {ok: false, level: "info", message: "onboarding.validation.save_flow_again"};
            }
            return {ok: true};
        },
    },
    {
        id: "background_runs_info",
        stepType: "inspection",
        title: "onboarding.steps.background_runs_info.title",
        description: "onboarding.steps.background_runs_info.description",
        showCompletionBadge: false,
        overlayPosition: {vertical: "middle", horizontal: "right"},
        validate: () => ({ok: true}),
    },
    {
        id: "finish",
        stepType: "finish",
        title: "onboarding.steps.finish.title",
        description: "onboarding.steps.finish.description",
        overlayPosition: {vertical: "middle", horizontal: "right"},
        validate: () => ({ok: true}),
    },
];

export const FIRST_FLOW_STEP_IDS = FIRST_FLOW_GUIDE_STEPS.map((step) => step.id);
