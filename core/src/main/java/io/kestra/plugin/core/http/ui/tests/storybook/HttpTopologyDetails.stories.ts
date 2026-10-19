import type { Meta, StoryObj } from "@storybook/vue3";
import { setup } from "@storybook/vue3";
import { expect, waitFor, within } from "storybook/test";
import { createI18n } from "vue-i18n";
import KestraDesignSystem from "@kestra-io/design-system";
import HttpTopologyDetails from "../../src/components/HttpTopologyDetails.vue";
import {
    downloadOutputs,
    downloadTask,
    execution,
    noRequestOutputs,
    outputs,
    requestTask,
    successOutputs,
} from "./httpSummary.fixtures";

// The component owns its messages (local i18n scope), so an empty global instance is enough.
const i18n = createI18n({ legacy: false, locale: "en", messages: { en: {} } });
setup((app) => {
    app.use(i18n);
    app.use(KestraDesignSystem);
});

const meta: Meta<typeof HttpTopologyDetails> = {
    title: "Plugin UI / topology-details / HttpTopologyDetails",
    component: HttpTopologyDetails,
    args: { taskType: requestTask.type, task: requestTask, execution, progress: [] },
};

export default meta;
type Story = StoryObj<typeof HttpTopologyDetails>;

const has = (root: HTMLElement, name: string) =>
    root.querySelector(`[data-test="${name}"]`) !== null;

export const Success: Story = {
    args: { fetchOutputs: outputs(successOutputs) },
    async play({ canvasElement }) {
        const canvas = within(canvasElement);
        await waitFor(() => expect(canvas.getByText("POST")).toBeVisible());
        expect(canvas.getByText("api.example.com/v1/items")).toBeVisible();
        expect(canvas.getByText("200 OK")).toBeVisible();
        expect(canvas.getByText("application/json")).toBeVisible();
        expect(canvas.getByText("2.0 KiB")).toBeVisible();
        expect(canvas.getByText("0.18s")).toBeVisible();
    },
};

export const NoRequestOutput: Story = {
    args: { fetchOutputs: outputs(noRequestOutputs) },
    async play({ canvasElement }) {
        const canvas = within(canvasElement);
        await waitFor(() => expect(canvas.getByText("204 No Content")).toBeVisible());
        expect(canvas.queryByText("Method")).toBeNull();
    },
};

export const Download: Story = {
    args: {
        taskType: downloadTask.type,
        task: downloadTask,
        fetchOutputs: outputs(downloadOutputs),
    },
    async play({ canvasElement }) {
        const canvas = within(canvasElement);
        await waitFor(() => expect(canvas.getByText("text/csv")).toBeVisible());
        expect(canvas.getByText("1.0 MiB")).toBeVisible();
    },
};

export const NoOutputsYet: Story = {
    args: { fetchOutputs: outputs({}) },
    async play({ canvasElement }) {
        await waitFor(() => expect(has(canvasElement, "http-summary-empty")).toBe(true));
    },
};

export const Loading: Story = {
    args: { fetchOutputs: () => new Promise(() => undefined) },
    async play({ canvasElement }) {
        await waitFor(() => expect(has(canvasElement, "http-summary-loading")).toBe(true));
    },
};

export const LoadError: Story = {
    args: { fetchOutputs: () => Promise.reject(new Error("boom")) },
    async play({ canvasElement }) {
        await waitFor(() => expect(has(canvasElement, "http-summary-error")).toBe(true));
    },
};

export const Dark: Story = {
    args: { fetchOutputs: outputs(successOutputs) },
    async play({ canvasElement }) {
        document.documentElement.classList.add("dark");
        try {
            await waitFor(() => expect(within(canvasElement).getByText("200 OK")).toBeVisible());
        } finally {
            document.documentElement.classList.remove("dark");
        }
    },
};