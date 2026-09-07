import ErrorToastContainer from "../../../src/components/ErrorToastContainer.vue";
import type {Meta, StoryObj} from "@storybook/vue3-vite";
import {ref} from "vue";
import type {ProblemFieldError} from "@kestra-io/kestra-sdk";
import {vueRouter} from "storybook-vue3-router";

const meta: Meta<typeof ErrorToastContainer> = {
    title: "components/ErrorToastContainer",
    component: ErrorToastContainer,
    decorators: [
        vueRouter([
            {path: "/", name: "home", component: {template: "<div />"}},
            {
                path: "/flows/:namespace/:id",
                name: "flows/update",
                component: {template: "<div />"},
            },
        ]),
    ],
}

export default meta;

export const SimpleError: StoryObj<typeof ErrorToastContainer> = {
    render: () => ({
        setup() {
            const detail = "An error occurred while processing your request";
            const items: ProblemFieldError[] = [];

            return () => (
                <div style="padding: 20px; background: #f5f5f5; border-radius: 8px;">
                    <ErrorToastContainer detail={detail} items={items} />
                </div>
            );
        }
    }),
}

export const ErrorWithItems: StoryObj<typeof ErrorToastContainer> = {
    render: () => ({
        setup() {
            const detail = "Validation failed for flow configuration";
            const items: ProblemFieldError[] = [
                {path: "tasks[processData].id", pointer: "/tasks/0/id", detail: "Task ID must be unique"},
                {path: "tasks[sendEmail].to", pointer: "/tasks/1/to", detail: "Email address is required"},
                {detail: "Flow must contain at least one task"}
            ];

            return () => (
                <div style="padding: 20px; background: #f5f5f5; border-radius: 8px;">
                    <ErrorToastContainer detail={detail} items={items} />
                </div>
            );
        }
    }),
}

export const ServerErrorWithTraceId: StoryObj<typeof ErrorToastContainer> = {
    render: () => ({
        setup() {
            const detail = "The service is temporarily unavailable";
            const items: ProblemFieldError[] = [];

            return () => (
                <div style="padding: 20px; background: #f5f5f5; border-radius: 8px;">
                    <p style="margin-bottom: 10px; color: #666;">
                        <strong>Note:</strong> a server error carries a traceId, which is the only link
                        between what the user saw and the log entry holding the real cause.
                    </p>
                    <ErrorToastContainer detail={detail} items={items} traceId="7b2f1c40a9e64c8f" />
                </div>
            );
        }
    }),
}

export const FlowContextWithAIButton: StoryObj<typeof ErrorToastContainer> = {
    render: () => ({
        setup() {
            const detail = "Syntax error in flow definition";
            const items: ProblemFieldError[] = [
                {path: "tasks[myTask].type", detail: "Unknown task type: 'io.kestra.plugin.invalid.Task'"},
                {path: "inputs[myInput].type", detail: "Input type 'INVALID' is not supported"}
            ];

            const handleClose = () => {
                console.log("Close notification clicked");
            };

            return () => (
                <div style="padding: 20px; background: #f5f5f5; border-radius: 8px;">
                    <p style="margin-bottom: 10px; color: #666;">
                        <strong>Note:</strong> AI Fix button is visible when route name is 'flows/update' or 'flows/create'.
                        This story simulates that context.
                    </p>
                    <ErrorToastContainer
                        detail={detail}
                        items={items}
                        onClose={handleClose}
                    />
                </div>
            );
        }
    }),
}

export const LongErrorMessage: StoryObj<typeof ErrorToastContainer> = {
    render: () => ({
        setup() {
            const detail = "Failed to execute workflow: The execution encountered multiple critical errors during the task processing phase. This could be due to configuration issues, resource constraints, or external service failures.";
            const items: ProblemFieldError[] = [
                {
                    path: "tasks[dataProcessing].config.database.connection",
                    detail: "Database connection timeout after 30 seconds. Please check your network configuration and database availability."
                },
                {
                    path: "tasks[apiCall].config.endpoint",
                    detail: "API endpoint returned 429 Too Many Requests. Rate limit exceeded. Please retry after 60 seconds."
                },
                {
                    path: "tasks[fileUpload].config.storage",
                    detail: "Storage quota exceeded. Current usage: 95GB of 100GB. Please clean up old files or upgrade your storage plan."
                }
            ];

            return () => (
                <div style="padding: 20px; background: #f5f5f5; border-radius: 8px; max-width: 600px;">
                    <ErrorToastContainer detail={detail} items={items} />
                </div>
            );
        }
    }),
}

export const MarkdownInError: StoryObj<typeof ErrorToastContainer> = {
    render: () => ({
        setup() {
            const detail = "**Authentication Failed**: Invalid API token. Please check your `credentials` configuration.\n\nFor more information, see the [documentation](https://kestra.io/docs).";
            const items: ProblemFieldError[] = [];

            return () => (
                <div style="padding: 20px; background: #f5f5f5; border-radius: 8px;">
                    <p style="margin-bottom: 10px; color: #666;">
                        <strong>Note:</strong> Markdown rendering is supported in error messages.
                    </p>
                    <ErrorToastContainer detail={detail} items={items} />
                </div>
            );
        }
    }),
}

export const MultipleValidationErrors: StoryObj<typeof ErrorToastContainer> = {
    render: () => ({
        setup() {
            const detail = "Flow validation failed with multiple errors";
            const items: ProblemFieldError[] = [
                {path: "id", pointer: "/id", detail: "Flow ID cannot be empty"},
                {path: "namespace", pointer: "/namespace", detail: "Namespace must match pattern: ^[a-z0-9._-]+$"},
                {path: "tasks[my-task].id", pointer: "/tasks/0/id", detail: "Task ID 'my-task' contains invalid characters"},
                {path: "tasks[my-task].type", pointer: "/tasks/0/type", detail: "Task type is required"},
                {path: "tasks[processData].id", pointer: "/tasks/1/id", detail: "Duplicate task ID: 'processData'"},
                {path: "triggers[daily].schedule", pointer: "/triggers/0/schedule", detail: "Invalid cron expression: '0 0 32 * *'"},
                {path: "inputs[myInput].type", pointer: "/inputs/0/type", detail: "Input type 'CUSTOM' is not recognized"},
                {path: "labels.env", pointer: "/labels/env", detail: "Label value exceeds maximum length of 100 characters"}
            ];

            return () => (
                <div style="padding: 20px; background: #f5f5f5; border-radius: 8px;">
                    <ErrorToastContainer detail={detail} items={items} />
                </div>
            );
        }
    }),
}

export const Interactive: StoryObj<typeof ErrorToastContainer> = {
    render: () => ({
        setup() {
            const detail = ref("Click 'Trigger Error' to simulate different error scenarios");
            const items = ref<ProblemFieldError[]>([]);
            const traceId = ref<string | undefined>(undefined);

            const errorScenarios: {name: string; detail: string; items: ProblemFieldError[]; traceId?: string}[] = [
                {
                    name: "Simple Error",
                    detail: "A simple error occurred",
                    items: []
                },
                {
                    name: "Validation Errors",
                    detail: "Validation failed",
                    items: [
                        {path: "tasks[myTask]", detail: "Task configuration is invalid"},
                        {path: "inputs[myInput]", detail: "Input value is required"}
                    ]
                },
                {
                    name: "Server Error",
                    detail: "Service unavailable",
                    items: [],
                    traceId: "7b2f1c40a9e64c8f"
                }
            ];

            let currentScenario = 0;

            const triggerError = () => {
                const scenario = errorScenarios[currentScenario];
                detail.value = scenario.detail;
                items.value = scenario.items;
                traceId.value = scenario.traceId;
                currentScenario = (currentScenario + 1) % errorScenarios.length;
            };

            return () => (
                <div style="padding: 20px; background: #f5f5f5; border-radius: 8px;">
                    <button
                        onClick={triggerError}
                        style="margin-bottom: 20px; padding: 10px 20px; background: #409eff; color: white; border: none; border-radius: 4px; cursor: pointer;"
                    >
                        Trigger Next Error Scenario
                    </button>
                    <ErrorToastContainer detail={detail.value} items={items.value} traceId={traceId.value} />
                </div>
            );
        }
    }),
}
