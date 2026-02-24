import TaskBasic from "@/components/no-code/components/tasks/TaskBasic.vue";
import {ref} from "vue"
import {StoryObj} from "@storybook/vue3-vite";
import {within, expect} from "storybook/test";
import {vueRouter} from "storybook-vue3-router";

export default {
    decorators: [vueRouter([
        {
            path: "/",
            name: "home",
            component: {template: "<div>home</div>"}
        }])
    ],
    title: "Components/NoCode/TaskBasic",
    component: TaskBasic,
}

type Story = StoryObj<typeof TaskBasic>;

// Schema matching io.kestra.plugin.core.log.Log
// with "message" as a required field
const logTaskSchema = {
    type: "object",
    properties: {
        message: {
            type: "string",
            title: "Message to log",
            description: "The message to write to the log"
        },
        level: {
            type: "string",
            title: "Log level",
            description: "The log level",
            enum: ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"],
            default: "INFO"
        }
    },
    required: ["message"]
};

const LogTaskRender = () => ({
    setup() {
        const model = ref<Record<string, any> | undefined>({})
        return () => <div style={{display: "flex", gap: "16px"}}>
            <div style={{width: "500px"}}>
                <TaskBasic
                    schema={logTaskSchema}
                    modelValue={model.value}
                    onUpdate:modelValue={(value: Record<string, any> | undefined) => model.value = value}
                />
            </div>
            <div style={{width: "500px"}}>
                <h2>Resulting object</h2>
                <pre style={{
                    border: "1px solid var(--ks-border-primary)",
                    borderRadius: "4px",
                    padding: "2px",
                    background: "var(--ks-background-card)"
                }} data-testid="resulting-object">{JSON.stringify(model.value, null, 2)}</pre>
            </div>
        </div>
    }
});

export const LogTask: Story = {
    render: LogTaskRender,
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        
        // Find all asterisks (required field indicators)
        const asterisks = canvas.getAllByText("*", {selector: "span.text-danger"});
        
        // Should have at least one asterisk for the required "message" field
        expect(asterisks.length).toBeGreaterThanOrEqual(1);
        
        // Verify the asterisk is next to the Message label
        const messageLabel = await canvas.findByText("Message");
        expect(messageLabel).toBeVisible();
        
        // The asterisk should be in the same parent element as the Message label
        const parentElement = messageLabel.parentElement;
        const asteriskInParent = parentElement?.querySelector("span.text-danger");
        expect(asteriskInParent).toBeInTheDocument();
        expect(asteriskInParent?.textContent).toBe("*");
    }
}