import {ref} from "vue";
import {expect} from "storybook/test";
import type {Meta, StoryObj} from "@storybook/vue3-vite";
import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import Editor from "../../../../src/components/inputs/Editor.vue";
import {fillExpressionCache} from "../../../../src/services/autoCompletionProvider";

const MOCK_FILTERS = ["upper", "lower", "capitalize", "trim", "date", "number"];
const MOCK_FUNCTIONS = ["now", "chunk", "max", "min", "range"];

const meta: Meta<typeof Editor> = {
    title: "Components/Inputs/Editor",
    component: Editor,
}

export default meta;

type Story = StoryObj<typeof Editor>;

function getMonacoEditor(canvasElement: HTMLElement): monaco.editor.ICodeEditor | undefined {
    const editorEl = canvasElement.querySelector(".monaco-editor") as HTMLElement | null;
    if (!editorEl) return undefined;
    return monaco.editor.getEditors().find(e => editorEl.contains(e.getDomNode()!));
}

function suggestionLabels(editorEl: HTMLElement): string[] {
    const suggestWidget = editorEl.querySelector(".suggest-widget");
    if (!suggestWidget) return [];
    return Array.from(suggestWidget.querySelectorAll(".monaco-list-row .label-name"))
        .map(el => el.textContent?.trim() ?? "");
}

export const Default: Story = {
    render: () => ({
        setup() {
            const value = ref("");

            return () => (
                <div style="height: 500px; width: 100%;">
                    <Editor
                        modelValue={value.value}
                        onUpdate:modelValue={(v: string) => value.value = v}
                        lang="yaml-pebble"
                        navbar={false}
                        fullHeight={true}
                    />
                </div>
            );
        },
    }),
};

export const PebbleFilterAutocomplete: Story = {
    render: () => ({
        setup() {
            const value = ref("{{ 'hello' ");

            return () => (
                <div style="height: 500px; width: 100%;">
                    <Editor
                        modelValue={value.value}
                        onUpdate:modelValue={(v: string) => value.value = v}
                        lang="yaml-pebble"
                        navbar={false}
                        fullHeight={true}
                    />
                </div>
            );
        },
    }),
    play: async ({canvasElement}) => {
        fillExpressionCache(MOCK_FILTERS, MOCK_FUNCTIONS);

        // Wait for Monaco editor to mount
        await new Promise(resolve => setTimeout(resolve, 1000));

        const editorEl = canvasElement.querySelector(".monaco-editor") as HTMLElement;
        expect(editorEl).toBeTruthy();

        const codeEditor = getMonacoEditor(canvasElement);
        expect(codeEditor).toBeTruthy();

        codeEditor!.focus();
        // Set value with pipe at end inside pebble expression, then type "|" to trigger filter completion
        const model = codeEditor!.getModel()!;
        const endPos = model.getPositionAt(model.getValueLength());
        codeEditor!.setPosition(endPos);

        // Type a pipe character via the editor API to trigger the completion provider
        codeEditor!.trigger("test", "type", {text: "|"});
        // Also explicitly trigger suggest in case the trigger character didn't fire
        codeEditor!.trigger("test", "editor.action.triggerSuggest", {});

        await new Promise(resolve => setTimeout(resolve, 2000));

        // The suggest widget should appear with filter suggestions
        const suggestWidget = editorEl.querySelector(".suggest-widget");
        expect(suggestWidget).toBeTruthy();

        // Assert at least one known filter is present
        const labels = suggestionLabels(editorEl);
        expect(labels.some(l => l.includes("upper"))).toBeTruthy();
    },
};

export const PebbleRootFieldAutocomplete: Story = {
    render: () => ({
        setup() {
            const value = ref("{{ out");

            return () => (
                <div style="height: 500px; width: 100%;">
                    <Editor
                        modelValue={value.value}
                        onUpdate:modelValue={(v: string) => value.value = v}
                        lang="yaml-pebble"
                        navbar={false}
                        fullHeight={true}
                    />
                </div>
            );
        },
    }),
    play: async ({canvasElement}) => {
        fillExpressionCache(MOCK_FILTERS, MOCK_FUNCTIONS);

        // Wait for Monaco editor to mount
        await new Promise(resolve => setTimeout(resolve, 1000));

        const editorEl = canvasElement.querySelector(".monaco-editor") as HTMLElement;
        expect(editorEl).toBeTruthy();

        const codeEditor = getMonacoEditor(canvasElement);
        expect(codeEditor).toBeTruthy();

        codeEditor!.focus();
        // Content is "{{ out" — place cursor at end and trigger suggest
        const model = codeEditor!.getModel()!;
        const endPos = model.getPositionAt(model.getValueLength());
        codeEditor!.setPosition(endPos);

        codeEditor!.trigger("test", "editor.action.triggerSuggest", {});

        await new Promise(resolve => setTimeout(resolve, 2000));

        // The suggest widget should appear with root fields and function suggestions
        const suggestWidget = editorEl.querySelector(".suggest-widget");
        expect(suggestWidget).toBeTruthy();

        // Assert at least one root variable is present
        const labels = suggestionLabels(editorEl);
        expect(labels.some(l => l.includes("outputs"))).toBeTruthy();
    },
};

export const PebbleFilterAutocompleteOutsideExpression: Story = {
    render: () => ({
        setup() {
            const value = ref("hello");

            return () => (
                <div style="height: 500px; width: 100%;">
                    <Editor
                        modelValue={value.value}
                        onUpdate:modelValue={(v: string) => value.value = v}
                        lang="yaml-pebble"
                        navbar={false}
                        fullHeight={true}
                    />
                </div>
            );
        },
    }),
    play: async ({canvasElement}) => {
        fillExpressionCache(MOCK_FILTERS, MOCK_FUNCTIONS);

        await new Promise(resolve => setTimeout(resolve, 1000));

        const editorEl = canvasElement.querySelector(".monaco-editor") as HTMLElement;
        expect(editorEl).toBeTruthy();

        const codeEditor = getMonacoEditor(canvasElement);
        expect(codeEditor).toBeTruthy();

        // Dismiss any leftover suggest widget from previous tests
        codeEditor!.trigger("test", "hideSuggestWidget", {});

        codeEditor!.focus();
        const model = codeEditor!.getModel()!;
        const endPos = model.getPositionAt(model.getValueLength());
        codeEditor!.setPosition(endPos);

        // Type pipe outside of {{ }} — should NOT show filter suggestions
        codeEditor!.trigger("test", "type", {text: "|"});

        await new Promise(resolve => setTimeout(resolve, 2000));

        // The suggest widget should NOT be visible (no .visible class, or no filter labels)
        const labels = suggestionLabels(editorEl);
        const hasFilterSuggestions = labels.some(l => MOCK_FILTERS.includes(l));
        expect(hasFilterSuggestions).toBeFalsy();
    },
};
