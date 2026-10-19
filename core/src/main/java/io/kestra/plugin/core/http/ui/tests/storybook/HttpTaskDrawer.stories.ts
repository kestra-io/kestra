import type { Meta, StoryObj } from "@storybook/vue3";
import { setup } from "@storybook/vue3";
import { expect, waitFor, within } from "storybook/test";
import { createI18n } from "vue-i18n";
import KestraDesignSystem from "@kestra-io/design-system";
import HttpTaskDrawer from "../../src/components/HttpTaskDrawer.vue";
import {
    downloadOutputs,
    downloadTask,
    encryptedBodyOutputs,
    execution,
    noRequestOutputs,
    outputs,
    redactedHeadersOutputs,
    redirectRetryOutputs,
    requestTask,
    successOutputs,
} from "./httpSummary.fixtures";

const i18n = createI18n({ legacy: false, locale: "en", messages: { en: {} } });
setup((app) => {
    app.use(i18n);
    app.use(KestraDesignSystem);
});

const meta: Meta<typeof HttpTaskDrawer> = {
    title: "Plugin UI / topology-task-drawer / HttpTaskDrawer",
    component: HttpTaskDrawer,
    args: { taskType: requestTask.type, task: requestTask, execution, progress: [] },
};

export default meta;
type Story = StoryObj<typeof HttpTaskDrawer>;

const find = (root: HTMLElement, name: string) => root.querySelector(`[data-test="${name}"]`);

export const Success: Story = {
    args: { fetchOutputs: outputs(successOutputs) },
    async play({ canvasElement }) {
        const canvas = within(canvasElement);
        await waitFor(() => expect(canvas.getByText("200 OK")).toBeVisible());
        expect(canvas.getByText("api.example.com")).toBeVisible();
        expect(find(canvasElement, "http-summary-body-text")?.textContent).toContain(
            '"name": "item"',
        );
        expect(find(canvasElement, "http-summary-redirects")).toBeNull();
    },
};

export const RedirectsAndRetries: Story = {
    args: { fetchOutputs: outputs(redirectRetryOutputs) },
    async play({ canvasElement }) {
        await waitFor(() => expect(find(canvasElement, "http-summary-redirects")).not.toBeNull());
        expect(within(canvasElement).getAllByText("301")).toHaveLength(1);
        expect(
            within(canvasElement).getAllByText("https://api.example.com/v2/items").length,
        ).toBeGreaterThan(1);
    },
};

export const RedactedHeaders: Story = {
    args: { fetchOutputs: outputs(redactedHeadersOutputs) },
    async play({ canvasElement }) {
        await waitFor(() => expect(within(canvasElement).getByText("set-cookie")).toBeVisible());
        expect(within(canvasElement).getByText("redacted")).toBeVisible();
    },
};

export const EncryptedBody: Story = {
    args: { fetchOutputs: outputs(encryptedBodyOutputs) },
    async play({ canvasElement }) {
        await waitFor(() =>
            expect(find(canvasElement, "http-summary-body-encrypted")).not.toBeNull(),
        );
        expect(find(canvasElement, "http-summary-body-text")).toBeNull();
        expect(canvasElement.textContent).not.toContain("ciphertext");
    },
};

export const NoRequestOutput: Story = {
    args: { fetchOutputs: outputs(noRequestOutputs) },
    async play({ canvasElement }) {
        await waitFor(() => expect(find(canvasElement, "http-summary-no-request")).not.toBeNull());
        expect(within(canvasElement).getByText("204 No Content")).toBeVisible();
    },
};

export const Download: Story = {
    args: {
        taskType: downloadTask.type,
        task: downloadTask,
        fetchOutputs: outputs(downloadOutputs),
    },
    async play({ canvasElement }) {
        await waitFor(() => expect(find(canvasElement, "http-summary-file")).not.toBeNull());
        expect(canvasElement.textContent).toContain("data.csv");
        expect(find(canvasElement, "http-summary-body")).toBeNull();
    },
};

export const NoOutputsYet: Story = {
    args: { fetchOutputs: outputs({}) },
    async play({ canvasElement }) {
        await waitFor(() => expect(find(canvasElement, "http-summary-empty")).not.toBeNull());
    },
};

export const NoExecution: Story = {
    args: { execution: undefined, fetchOutputs: undefined },
    async play({ canvasElement }) {
        await waitFor(() => expect(find(canvasElement, "http-summary-empty")).not.toBeNull());
    },
};

export const Loading: Story = {
    args: { fetchOutputs: () => new Promise(() => undefined) },
    async play({ canvasElement }) {
        await waitFor(() => expect(find(canvasElement, "http-summary-loading")).not.toBeNull());
    },
};

export const LoadError: Story = {
    args: { fetchOutputs: () => Promise.reject(new Error("boom")) },
    async play({ canvasElement }) {
        await waitFor(() => expect(find(canvasElement, "http-summary-error")).not.toBeNull());
    },
};

export const Dark: Story = {
    args: { fetchOutputs: outputs(redirectRetryOutputs) },
    async play({ canvasElement }) {
        document.documentElement.classList.add("dark");
        try {
            await waitFor(() => expect(within(canvasElement).getByText("200 OK")).toBeVisible());
        } finally {
            document.documentElement.classList.remove("dark");
        }
    },
};