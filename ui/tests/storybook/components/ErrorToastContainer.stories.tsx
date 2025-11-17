import ErrorToastContainer from "../../../src/components/ErrorToastContainer.vue";
import type {Meta, StoryObj} from "@storybook/vue3-vite";
import {ref} from "vue";

const meta: Meta<typeof ErrorToastContainer> = {
    title: "components/ErrorToastContainer",
    component: ErrorToastContainer,
}

export default meta;

export const SimpleError: StoryObj<typeof ErrorToastContainer> = {
    render: () => ({
        setup() {
            const message = {
                message: "An error occurred while processing your request",
                content: {
                    message: "An error occurred while processing your request"
                }
            };
            const items: any[] = [];

            return () => (
                <div style="padding: 20px; background: #f5f5f5; border-radius: 8px;">
                    <ErrorToastContainer message={message} items={items} />
                </div>
            );
        }
    }),
}

export const ErrorWithItems: StoryObj<typeof ErrorToastContainer> = {
    render: () => ({
        setup() {
            const message = {
                message: "Validation failed for flow configuration",
                content: {
                    message: "Validation failed for flow configuration"
                }
            };
            const items = [
                {path: "tasks.processData.id", message: "Task ID must be unique"},
                {path: "tasks.sendEmail.to", message: "Email address is required"},
                {message: "Flow must contain at least one task"}
            ];

            return () => (
                <div style="padding: 20px; background: #f5f5f5; border-radius: 8px;">
                    <ErrorToastContainer message={message} items={items} />
                </div>
            );
        }
    }),
}

export const ServiceUnavailable503: StoryObj<typeof ErrorToastContainer> = {
    render: () => ({
        setup() {
            const message = {
                message: "Server error",
                content: {
                    message: "The service is temporarily unavailable"
                },
                response: {
                    status: 503
                }
            };
            const items: any[] = [];

            return () => (
                <div style="padding: 20px; background: #f5f5f5; border-radius: 8px;">
                    <ErrorToastContainer message={message} items={items} />
                </div>
            );
        }
    }),
}

export const FlowContextWithAIButton: StoryObj<typeof ErrorToastContainer> = {
    render: () => ({
        setup() {
            const message = {
                message: "Syntax error in flow definition",
                content: {
                    message: "Syntax error in flow definition"
                }
            };
            const items = [
                {path: "tasks.myTask.type", message: "Unknown task type: 'io.kestra.plugin.invalid.Task'"},
                {path: "inputs.myInput.type", message: "Input type 'INVALID' is not supported"}
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
                        message={message} 
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
            const message = {
                message: "Failed to execute workflow: The execution encountered multiple critical errors during the task processing phase. This could be due to configuration issues, resource constraints, or external service failures.",
                content: {
                    message: "Failed to execute workflow: The execution encountered multiple critical errors during the task processing phase."
                }
            };
            const items = [
                { 
                    path: "tasks.dataProcessing.config.database.connection", 
                    message: "Database connection timeout after 30 seconds. Please check your network configuration and database availability."
                },
                { 
                    path: "tasks.apiCall.config.endpoint", 
                    message: "API endpoint returned 429 Too Many Requests. Rate limit exceeded. Please retry after 60 seconds."
                },
                { 
                    path: "tasks.fileUpload.config.storage", 
                    message: "Storage quota exceeded. Current usage: 95GB of 100GB. Please clean up old files or upgrade your storage plan."
                }
            ];

            return () => (
                <div style="padding: 20px; background: #f5f5f5; border-radius: 8px; max-width: 600px;">
                    <ErrorToastContainer message={message} items={items} />
                </div>
            );
        }
    }),
}

export const MarkdownInError: StoryObj<typeof ErrorToastContainer> = {
    render: () => ({
        setup() {
            const message = {
                message: "**Authentication Failed**: Invalid API token. Please check your `credentials` configuration.\n\nFor more information, see the [documentation](https://kestra.io/docs).",
                content: {
                    message: "**Authentication Failed**: Invalid API token."
                }
            };
            const items: any[] = [];

            return () => (
                <div style="padding: 20px; background: #f5f5f5; border-radius: 8px;">
                    <p style="margin-bottom: 10px; color: #666;">
                        <strong>Note:</strong> Markdown rendering is supported in error messages.
                    </p>
                    <ErrorToastContainer message={message} items={items} />
                </div>
            );
        }
    }),
}

export const MultipleValidationErrors: StoryObj<typeof ErrorToastContainer> = {
    render: () => ({
        setup() {
            const message = {
                message: "Flow validation failed with multiple errors",
                content: {
                    message: "Flow validation failed with multiple errors"
                }
            };
            const items = [
                {path: "id", message: "Flow ID cannot be empty"},
                {path: "namespace", message: "Namespace must match pattern: ^[a-z0-9._-]+$"},
                {path: "tasks[0].id", message: "Task ID 'my-task' contains invalid characters"},
                {path: "tasks[0].type", message: "Task type is required"},
                {path: "tasks[1].id", message: "Duplicate task ID: 'processData'"},
                {path: "triggers[0].schedule", message: "Invalid cron expression: '0 0 32 * *'"},
                {path: "inputs[0].type", message: "Input type 'CUSTOM' is not recognized"},
                {path: "labels.env", message: "Label value exceeds maximum length of 100 characters"}
            ];

            return () => (
                <div style="padding: 20px; background: #f5f5f5; border-radius: 8px;">
                    <ErrorToastContainer message={message} items={items} />
                </div>
            );
        }
    }),
}

export const Interactive: StoryObj<typeof ErrorToastContainer> = {
    render: () => ({
        setup() {
            const message = ref({
                message: "Click 'Trigger Error' to simulate different error scenarios",
                content: {
                    message: "Click 'Trigger Error' to simulate different error scenarios"
                }
            });
            const items = ref<any[]>([]);

            const errorScenarios = [
                {
                    name: "Simple Error",
                    message: {message: "A simple error occurred", content: {message: "A simple error occurred"}},
                    items: []
                },
                {
                    name: "Validation Errors",
                    message: {message: "Validation failed", content: {message: "Validation failed"}},
                    items: [
                        {path: "tasks.myTask", message: "Task configuration is invalid"},
                        {path: "inputs.myInput", message: "Input value is required"}
                    ]
                },
                {
                    name: "503 Error",
                    message: { 
                        message: "Service unavailable", 
                        content: {message: "Service unavailable"},
                        response: {status: 503}
                    },
                    items: []
                }
            ];

            let currentScenario = 0;

            const triggerError = () => {
                const scenario = errorScenarios[currentScenario];
                message.value = scenario.message;
                items.value = scenario.items;
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
                    <ErrorToastContainer message={message.value} items={items.value} />
                </div>
            );
        }
    }),
}

