import {computed, provide, ref} from "vue";
import TaskPluginImplementation from "./TaskPluginImplementation.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {expect, fireEvent, waitFor, within} from "storybook/test";
import {vueRouter} from "storybook-vue3-router";
import {SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../injectionKeys";
import type {Schema} from "./getTaskComponent";

const P = "io.kestra.plugin.ai.provider.";
const T = "io.kestra.plugin.ai.tool.";
const R = "io.kestra.plugin.ai.retriever.";
const O = "io.kestra.plugin.ai.domain.";

const definitions: Record<string, Schema> = {
    [`${P}GoogleGemini`]: {
        type: "object",
        title: "Language model provider",
        description: "Use Google Gemini models",
        properties: {
            type: {const: `${P}GoogleGemini`},
            modelName: {type: "string"},
            apiKey: {type: "string", $secret: true},
        },
        required: ["modelName"],
    },
    [`${P}OpenAI`]: {
        type: "object",
        title: "Language model provider",
        description: "Use OpenAI models",
        properties: {
            type: {const: `${P}OpenAI`},
            modelName: {type: "string"},
            apiKey: {type: "string", $secret: true},
        },
        required: ["modelName"],
    },
    [`${T}TavilyWebSearch`]: {
        type: "object",
        description: "Search the web with Tavily",
        properties: {
            type: {const: `${T}TavilyWebSearch`},
            apiKey: {type: "string", $secret: true},
        },
        required: ["apiKey"],
    },
    [`${T}KestraFlow`]: {
        type: "object",
        description: "Execute Kestra flows from an agent",
        properties: {
            type: {const: `${T}KestraFlow`},
            namespace: {type: "string"},
            flowId: {type: "string"},
        },
        required: [],
    },
    [`${R}EmbeddingStoreRetriever`]: {
        type: "object",
        description: "Retrieve context from an embedding store",
        properties: {
            type: {const: `${R}EmbeddingStoreRetriever`},
            embeddingProvider: {anyOf: [{$ref: `#/definitions/${P}GoogleGemini`}, {$ref: `#/definitions/${P}OpenAI`}]},
            maxResults: {type: "integer"},
        },
        required: ["embeddingProvider"],
    },
    [`${O}LangfuseObservability`]: {
        type: "object",
        description: "Traces this agent to Langfuse",
        properties: {
            type: {const: `${O}LangfuseObservability`},
            endpoint: {type: "string"},
            secretKey: {type: "string", $secret: true},
        },
        required: ["endpoint"],
    },
};

const meta: Meta<typeof TaskPluginImplementation> = {
    title: "Components/NoCode/TaskPluginImplementation",
    component: TaskPluginImplementation,
    decorators: [
        vueRouter([{path: "/", name: "home", component: {template: "<div>home</div>"}}]),
    ],
};

export default meta;

type Story = StoryObj<typeof TaskPluginImplementation>;

const renderWithResult: Story["render"] = (args) => ({
    setup() {
        provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => definitions));
        const model = ref(args.modelValue);
        return () => <div style={{display: "flex", gap: "16px"}}>
            <div style={{width: "560px"}}>
                <TaskPluginImplementation
                    modelValue={model.value}
                    onUpdate:modelValue={(val) => model.value = val}
                    schema={args.schema}
                    required={args.required}
                    root={args.root}
                />
            </div>
            <pre data-testid="result">{JSON.stringify(model.value, null, 2)}</pre>
        </div>
    },
});

const providerSchema = {anyOf: [{$ref: `#/definitions/${P}GoogleGemini`}, {$ref: `#/definitions/${P}OpenAI`}]} as Schema;
const toolsSchema = {type: "array", items: {anyOf: [{$ref: `#/definitions/${T}TavilyWebSearch`}, {$ref: `#/definitions/${T}KestraFlow`}]}} as Schema;
const observabilitySchema = {$ref: `#/definitions/${O}LangfuseObservability`} as Schema;
const contentRetrieversSchema = {type: "array", items: {anyOf: [{$ref: `#/definitions/${R}EmbeddingStoreRetriever`}]}} as Schema;

// App markup uses `data-test`, testing-library's `getByTestId` reads `data-testid` — the story's
// own `<pre data-testid="result">` is the only element meant to be queried that way.
const byTest = (root: ParentNode, name: string) => root.querySelector<HTMLElement>(`[data-test='${name}']`);
const allByTest = (root: ParentNode, name: string) => [...root.querySelectorAll<HTMLElement>(`[data-test='${name}']`)];

export const EmptyRequired: Story = {
    render: renderWithResult,
    args: {modelValue: undefined, schema: providerSchema, required: true, root: "provider"},
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        await canvas.findByText("Language model provider");
        expect(byTest(canvasElement, "plugin-implementation-count")?.textContent).toContain("2 available");
        expect(byTest(canvasElement, "field-required-missing")).toBeVisible();
        expect(byTest(canvasElement, "plugin-implementation-add")).toBeVisible();
    },
};

export const EmptyOptional: Story = {
    render: renderWithResult,
    args: {modelValue: undefined, schema: contentRetrieversSchema, required: false, root: "contentRetrievers"},
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        await canvas.findByText("Content retrievers");
        expect(byTest(canvasElement, "field-required-missing")).toBeNull();
    },
};

export const SetCollapsed: Story = {
    render: renderWithResult,
    args: {
        modelValue: {type: `${P}GoogleGemini`, modelName: "gemini-2.5-flash-lite", apiKey: "{{ secret('GEMINI_API_KEY') }}"},
        schema: providerSchema,
        required: true,
        root: "provider",
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        await waitFor(() => expect(byTest(canvasElement, "plugin-implementation")).toBeTruthy());
        const name = byTest(canvasElement, "plugin-implementation-item-name");
        expect(name?.textContent).toBe("Google Gemini");
        const summary = byTest(canvasElement, "plugin-implementation-item-summary");
        expect(summary?.textContent).toContain("gemini-2.5-flash-lite");
        expect(summary?.textContent).toContain("from secret");
        expect(canvas.getByText("modelName")).not.toBeVisible();

        await fireEvent.click(byTest(canvasElement, "plugin-implementation-toggle")!);
        await waitFor(() => expect(canvas.getByText("modelName")).toBeVisible());
    },
};

export const Repeated: Story = {
    render: renderWithResult,
    args: {
        modelValue: [
            {type: `${T}TavilyWebSearch`, apiKey: "{{ secret('TAVILY_API_KEY') }}"},
            {type: `${T}KestraFlow`, namespace: "company.team", flowId: "enrich-customer"},
        ],
        schema: toolsSchema,
        required: false,
        root: "tools",
    },
    async play({canvasElement}) {
        await waitFor(() => expect(byTest(canvasElement, "plugin-implementation")).toBeTruthy());
        expect(allByTest(canvasElement, "plugin-implementation-card")).toHaveLength(2);

        const removeButtons = allByTest(canvasElement, "plugin-implementation-remove");
        await fireEvent.click(removeButtons[0]);
        await waitFor(() => expect(allByTest(canvasElement, "plugin-implementation-card")).toHaveLength(1));
    },
};

export const ExactlyOneUnset: Story = {
    render: renderWithResult,
    args: {modelValue: undefined, schema: observabilitySchema, required: false, root: "observability"},
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        expect(canvas.getByText("1 implementation")).toBeVisible();

        await fireEvent.click(byTest(canvasElement, "plugin-implementation-add")!);
        const result = canvas.getByTestId("result");
        await waitFor(() => expect(JSON.parse(result.textContent ?? "null")).toEqual({type: `${O}LangfuseObservability`}));
    },
};

export const ExactlyOneSet: Story = {
    render: renderWithResult,
    args: {
        modelValue: {type: `${O}LangfuseObservability`, endpoint: "https://cloud.langfuse.com", secretKey: "{{ secret('LANGFUSE_SECRET_KEY') }}"},
        schema: observabilitySchema,
        required: false,
        root: "observability",
    },
    async play({canvasElement}) {
        await waitFor(() => expect(byTest(canvasElement, "plugin-implementation")).toBeTruthy());
        expect(byTest(canvasElement, "plugin-implementation-change")).toBeNull();
        expect(byTest(canvasElement, "plugin-implementation-remove")).toBeVisible();
    },
};

export const Nested: Story = {
    render: renderWithResult,
    args: {
        modelValue: [
            {
                type: `${R}EmbeddingStoreRetriever`,
                embeddingProvider: {type: `${P}GoogleGemini`, modelName: "gemini-embedding-001"},
            },
        ],
        schema: contentRetrieversSchema,
        required: false,
        root: "contentRetrievers",
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        await fireEvent.click(byTest(canvasElement, "plugin-implementation-toggle")!);
        await canvas.findByText("Language model provider");
        expect(allByTest(canvasElement, "plugin-implementation-name").length).toBeGreaterThan(0);
    },
};

export const UnresolvableType: Story = {
    render: renderWithResult,
    args: {
        modelValue: {type: `${P}Groq`, modelName: "llama-3.3-70b-versatile"},
        schema: providerSchema,
        required: true,
        root: "provider",
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        await waitFor(() => expect(byTest(canvasElement, "plugin-implementation")).toBeTruthy());
        const name = byTest(canvasElement, "plugin-implementation-item-name");
        expect(name?.textContent).toBe("Unknown implementation");
        expect(byTest(canvasElement, "plugin-implementation-raw")?.textContent).toContain(`${P}Groq`);

        const result = canvas.getByTestId("result");
        expect(JSON.parse(result.textContent ?? "null")).toEqual({type: `${P}Groq`, modelName: "llama-3.3-70b-versatile"});
    },
};
