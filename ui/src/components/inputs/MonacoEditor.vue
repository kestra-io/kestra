<template>
    <div class="ks-monaco-editor" ref="editorRef" />
</template>

<script lang="ts" setup>
    import {computed, h, onBeforeUnmount, onMounted, ref, render, watch} from "vue";
    import {useStore} from "vuex";

    import "monaco-editor/esm/vs/editor/editor.all.js";
    import "monaco-editor/esm/vs/editor/standalone/browser/iPadShowKeyboard/iPadShowKeyboard.js";
    import "monaco-editor/esm/vs/editor/standalone/browser/quickAccess/standaloneCommandsQuickAccess.js"
    import "monaco-editor/esm/vs/language/json/monaco.contribution";
    import "monaco-editor/esm/vs/basic-languages/monaco.contribution";
    import {ILanguageFeaturesService} from "monaco-editor/esm/vs/editor/common/services/languageFeatures"
    import {StandaloneServices} from "monaco-editor/esm/vs/editor/standalone/browser/standaloneServices"
    import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
    import {editor as MonacoAPIEditor, IPosition, languages} from "monaco-editor/esm/vs/editor/editor.api";
    import EditorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
    import JsonWorker from "monaco-editor/esm/vs/language/json/json.worker?worker";
    import {configureMonacoYaml} from "monaco-yaml";

    import YamlWorker from "./yaml.worker.js?worker";
    import {yamlSchemas} from "override/utils/yamlSchemas";
    import Utils from "../../utils/utils";
    import {TaskIcon, YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";
    import {QUOTE, YamlNoAutoCompletion} from "../../services/autoCompletionProvider.js"
    import {FlowAutoCompletion} from "override/services/flowAutoCompletionProvider.js";
    import RegexProvider from "../../utils/regex";
    import uniqBy from "lodash/uniqBy";
    import IModel = MonacoAPIEditor.IModel;
    import CompletionList = languages.CompletionList;
    import ProviderResult = languages.ProviderResult;
    import CompletionItem = languages.CompletionItem;

    const store = useStore();

    window.MonacoEnvironment = {
        getWorker(_moduleId, label) {
            switch (label) {
            case "editorWorkerService":
                return new EditorWorker();
            case "yaml":
                return new YamlWorker();
            case "json":
                return new JsonWorker();
            default:
                throw new Error(`Unknown label ${label}`);
            }
        },
    };

    monaco.editor.defineTheme("dark", {
        base: "vs-dark",
        inherit: true,
        rules: [{token: "", background: "161822"}],
        colors: {
            "minimap.background": "#161822",
        }
    });

    monaco.editor.defineTheme("light", {
        base: "vs",
        inherit: true,
        rules: [
            {token: "type", foreground: "#8405FF"},
            {token: "string.yaml", foreground: "#001233"},
            {token: "comment", foreground: "#8d99ae", fontStyle: "italic"},
        ],
        colors: {
            "editor.lineHighlightBackground": "#fbfaff",
            "editorLineNumber.foreground": "#444444",
            "editor.selectionBackground": "#E8E5FF",
            "editor.wordHighlightBackground": "#E8E5FF",
        }
    });

    let localEditor: monaco.editor.IStandaloneCodeEditor | null = null;
    let localDiffEditor: monaco.editor.IStandaloneDiffEditor | null = null;
    const autoCompletionProviders = ref<monaco.IDisposable[]>([])

    const suggestWidgetResizeObserver = ref<MutationObserver>()
    const suggestWidgetIconsObserver = ref<MutationObserver>()
    const suggestWidget = ref<HTMLElement>()

    const props = withDefaults(defineProps<{
        path?: string,
        original?: string,
        value: string,
        theme?: "light" | "dark",
        language?: string,
        extension?: string,
        options?: monaco.editor.IStandaloneEditorConstructionOptions & {renderSideBySide?: boolean},
        schemaType?: string,
        diffEditor?: boolean,
        input?: boolean,
        creating?: boolean
    }>(), {
        path: "",
        original: "",
        theme: "light",
        diffEditor: false,
        input: false,
        creating: false,
        language: undefined,
        extension: undefined,
        options: undefined,
        schemaType: undefined,
    })

    const emit = defineEmits(["editorDidMount", "change"])

    defineExpose({
        focus,
        destroy,
        monaco,
    })

    const editorResolved = computed(() => {
        return props.diffEditor ? localDiffEditor : localEditor;
    })

    const editorRef = ref<HTMLDivElement | null>(null);

    watch(() => props.path, (newValue, oldValue) => {
        if (newValue !== oldValue) {
            changeTab(newValue, () => Promise.resolve(props.value));
        }
    });

    watch(() => props.options, (newValue, oldValue) => {
        if (editorResolved.value && needReload(newValue, oldValue)) {
            reload();
        } else {
            localEditor?.updateOptions(newValue ?? {});
        }
    }, {deep: true});

    watch(() => props.value, (newValue) => {
        if (localEditor) {
            const modifiedEditor = getModifiedEditor();
            if (newValue !== modifiedEditor?.getValue()) {
                modifiedEditor?.setValue(newValue);
            }
        }
    });

    watch(() => props.original, (newValue) => {
        if (localEditor && props.diffEditor) {
            const originalEditor = getOriginalEditor();
            if (newValue !== originalEditor?.getValue()) {
                originalEditor?.setValue(newValue);
            }
        }
    });

    watch(() => props.theme, (newVal) => {
        if (editorResolved.value) {
            monaco.editor.setTheme(newVal);
        }
    });

    watch(suggestWidget, (newVal) => {
        const replaceRowsIcons = (nodes: HTMLElement[]) => {
            nodes = uniqBy(nodes, node => node.id);
            for (let node of nodes) {
                const maybeTaskName = node?.getAttribute("aria-label");
                if (!maybeTaskName || node.getAttribute("data-index") === null) {
                    continue;
                }

                const vsCodeIcon = node.querySelector(".suggest-icon") as HTMLElement;
                const taskIcon = node.querySelector(".wrapper:has(.icon)");

                if (maybeTaskName.includes(".")) {
                    if (store.state.plugin.icons[maybeTaskName] !== undefined) {
                        vsCodeIcon.style.display = "none";

                        const tempContainer = document.createElement("div");
                        render(h(TaskIcon, {
                            cls: maybeTaskName,
                            class: "w-auto h-auto me-1",
                            "only-icon": true,
                            icons: store.state.plugin.icons
                        }), tempContainer);

                        if (taskIcon !== null) {
                            taskIcon.replaceWith(tempContainer.firstElementChild!);
                        } else {
                            vsCodeIcon.after(tempContainer.firstElementChild!);
                        }
                        tempContainer.remove();
                    }
                } else {
                    vsCodeIcon.style.display = "revert";
                    taskIcon?.remove();
                }
            }
        };

        if (newVal !== undefined) {
            if (newVal.querySelector(".monaco-list-row") !== null) {
                replaceRowsIcons([...newVal.getElementsByClassName("monaco-list-row")] as HTMLElement[]);
            }

            suggestWidgetIconsObserver.value?.disconnect();
            suggestWidgetIconsObserver.value = undefined;

            suggestWidgetIconsObserver.value = new MutationObserver(mutations => {
                replaceRowsIcons(
                    mutations.flatMap(({addedNodes}) => {
                        const nodes = [...addedNodes];
                        const maybeRows = nodes.filter((n) => (n as HTMLElement).classList?.contains("monaco-list-row"));

                        for (let node of nodes) {
                            let maybeRow = null;
                            if (node instanceof Text) {
                                maybeRow = node.parentElement?.closest(".monaco-list-row");
                            }

                            if (maybeRow !== null) {
                                return [...maybeRows, maybeRow];
                            }
                        }

                        return maybeRows;
                    }) as HTMLElement[]
                );
            });

            suggestWidgetIconsObserver.value.observe(newVal, {childList: true, subtree: true});
        }
    });

    onMounted(async function () {
        // assign monaco so that it gets available outside of monacoeditor

        await document.fonts.ready.then(() => {
            initMonaco()
        })

        if (!store.state.core.monacoYamlConfigured && props.language === "yaml") {
            store.commit("core/setMonacoYamlConfigured", true);
            configureMonacoYaml(monaco, {
                enableSchemaRequest: true,
                hover: true,
                completion: true,
                validate: true,
                format: true,
                schemas: yamlSchemas(store)
            });

            const yamlCompletion = (StandaloneServices.get(ILanguageFeaturesService).completionProvider._entries as {
                selector: string,
                provider: {
                    provideCompletionItems: (model: IModel, position: IPosition) => ProviderResult<CompletionList>
                }
            }[]).find(completion => completion.selector === "yaml");

            if (yamlCompletion !== undefined) {
                const initialCompletion = yamlCompletion.provider.provideCompletionItems;
                yamlCompletion.provider.provideCompletionItems = async function (model: IModel, position: IPosition) {
                    const defaultCompletion = await initialCompletion(model, position);
                    if (!defaultCompletion) {
                        return defaultCompletion;
                    }

                    (defaultCompletion.suggestions as {
                        label: string,
                        filterText: string,
                        insertText: string
                    }[]).forEach(suggestion => {
                        if (suggestion.label.endsWith("...") && suggestion.insertText.includes(suggestion.label.substring(0, suggestion.label.length - 3))) {
                            suggestion.label = suggestion.insertText;
                        }

                        if (suggestion.label.includes(".")) {
                            const dotSplit = suggestion.label.split(/\.(?=\w)/);
                            const taskName = dotSplit.pop();
                            suggestion.filterText = [taskName, ...dotSplit, taskName].join(".");
                        }
                    });


                    return defaultCompletion;
                };
            }
        }

        await addKestraAutoCompletions();

        // Exposing functions globally for testing purposes
        (window as any).pasteToEditor = (textToPaste: string) => {
            localEditor?.executeEdits("", [{
                range: localEditor?.getSelection() ?? new monaco.Range(0, 0, 0, 0),
                text: textToPaste
            }])
        };
        (window as any).clearEditor = () => {
            localEditor?.getModel()?.setValue("")
        };
    })

    onBeforeUnmount(function () {
        destroy();
    })

    // ...mapMutations("editor", ["setTabDirty"]),
    function disposeObservers() {
        const swio = suggestWidgetResizeObserver.value
        if (swio !== undefined) {
            swio!.disconnect();
            suggestWidgetResizeObserver.value = undefined;
        }
        if (swio !== undefined) {
            swio!.disconnect();
            suggestWidgetResizeObserver.value = undefined;
        }
        suggestWidget.value = undefined;
    }

    async function addKestraAutoCompletions() {
        const NO_SUGGESTIONS = {suggestions: []};

        let yamlAutoCompletionProvider: YamlNoAutoCompletion;
        if (props.schemaType === "flow") {
            yamlAutoCompletionProvider = new FlowAutoCompletion(store);
        } else {
            yamlAutoCompletionProvider = new YamlNoAutoCompletion();
        }

        const QUOTES = ["\"", "'"];
        const endOfWordColumn = (position: IPosition, model: IModel): number => {
            return position.column + (model.findNextMatch(
                RegexProvider.beforeSeparator(QUOTES),
                position,
                true,
                false,
                null,
                true
            )?.matches?.[0]?.length ?? 0);
        }

        autoCompletionProviders.value.push(monaco.languages.registerCompletionItemProvider("yaml", {
            triggerCharacters: [":"],
            async provideCompletionItems(model, position) {
                const source = model.getValue();
                const cursorPosition = model.getOffsetAt(position);
                const parsed = YAML_UTILS.parse(source, false);

                const currentWord = model.findPreviousMatch(RegexProvider.beforeSeparator(), position, true, false, null, true);
                const elementUnderCursor = YAML_UTILS.localizeElementAtIndex(source, cursorPosition);
                if (elementUnderCursor?.key === undefined) {
                    return NO_SUGGESTIONS;
                }

                const parentStartLine = model.getPositionAt(elementUnderCursor.range![0]).lineNumber;
                const autoCompletions = await yamlAutoCompletionProvider.valueAutoCompletion(source, parsed, elementUnderCursor);
                return {
                    suggestions: autoCompletions.map(autoCompletion => {
                        const [label, isKey] = autoCompletion.split(":") as [string, string | undefined];
                        let insertText = label;
                        const endColumn = endOfWordColumn(position, model);
                        if (isKey === undefined) {
                            if (source.charAt(cursorPosition - 1) === ":") {
                                insertText = ` ${label}`;
                            }
                        } else {
                            if (parentStartLine === position.lineNumber) {
                                insertText = `\n  ${label}: `;
                            } else {
                                insertText = model.getLineContent(position.lineNumber).charAt(endColumn - 1) === ":" ? label : `${label}: `;
                            }
                        }
                        return ({
                            kind: isKey === undefined ? monaco.languages.CompletionItemKind.Value : monaco.languages.CompletionItemKind.Property,
                            label,
                            insertText: insertText,
                            range: {
                                startLineNumber: position.lineNumber,
                                endLineNumber: position.lineNumber,
                                startColumn: position.column - (currentWord?.matches?.[0]?.length ?? 0),
                                endColumn: endColumn
                            }
                        });
                    })
                };
            }
        }));

        const propertySuggestion = (value: string, position: {
            lineNumber: number,
            startColumn: number,
            endColumn: number
        }, kind?: monaco.languages.CompletionItemKind): CompletionItem => {
            let label = value.split("(")[0];
            if (label.startsWith(QUOTE) && label.endsWith(QUOTE)) {
                label = label.substring(1, label.length - 1);
            }

            return ({
                kind: kind ?? (value.includes("(") ? monaco.languages.CompletionItemKind.Function : monaco.languages.CompletionItemKind.Property),
                label: label,
                insertText: value,
                insertTextRules: value.includes("${1:") ? monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet : undefined,
                range: {
                    startLineNumber: position.lineNumber,
                    endLineNumber: position.lineNumber,
                    startColumn: position.startColumn,
                    endColumn: position.endColumn
                }
            });
        };

        autoCompletionProviders.value.push(monaco.languages.registerCompletionItemProvider(["yaml", "plaintext"], {
            triggerCharacters: ["{"],
            async provideCompletionItems(model, position) {
                // Not a subfield access
                const rootPebbleVariableMatcher = model.findPreviousMatch(RegexProvider.capturePebbleVarRoot + "$", position, true, false, null, true);
                if (rootPebbleVariableMatcher === null || rootPebbleVariableMatcher.matches === null) {
                    return NO_SUGGESTIONS;
                }

                const startOfWordColumn = position.column - rootPebbleVariableMatcher.matches[1].length;
                return {
                    suggestions: (await (yamlAutoCompletionProvider.rootFieldAutoCompletion()))
                        .map(s => propertySuggestion(s, {
                            lineNumber: position.lineNumber,
                            startColumn: startOfWordColumn,
                            endColumn: endOfWordColumn(position, model)
                        }))
                };
            }
        }));

        autoCompletionProviders.value.push(monaco.languages.registerCompletionItemProvider(["yaml", "plaintext"], {
            triggerCharacters: ["("],
            async provideCompletionItems(model, position) {
                const source = model.getValue();
                const parsed = YAML_UTILS.parse(source, false);

                const functionMatcher = model.findPreviousMatch(RegexProvider.capturePebbleFunction + "$", position, true, false, null, true);
                if (functionMatcher === null || functionMatcher.matches === null) {
                    return NO_SUGGESTIONS;
                }

                const wordStartOffset = functionMatcher.matches?.[3]?.length
                    ?? (model.findPreviousMatch(RegexProvider.beforeSeparator(QUOTES) + "$", position, true, false, null, true)?.matches?.[0]?.length)
                    ?? 0;
                const startOfWordColumn = position.column - wordStartOffset;
                return {
                    suggestions: (await yamlAutoCompletionProvider.functionAutoCompletion(
                        parsed,
                        functionMatcher.matches[1],
                        Object.fromEntries(functionMatcher.matches?.[2]?.split(/ *, */)?.map(arg => arg.split(/ *= */)) ?? []))
                    ).map(s => {
                        const endColumn = endOfWordColumn(position, model);
                        const suggestion = propertySuggestion(s, {
                            lineNumber: position.lineNumber,
                            startColumn: startOfWordColumn,
                            endColumn: endColumn
                        }, monaco.languages.CompletionItemKind.Value);

                        // If the inserted value is a string (surrounded by quotes), we remove them if there is already one
                        if (suggestion.insertText.startsWith(QUOTE) && suggestion.insertText.endsWith(QUOTE)) {
                            const lineContent = model.getLineContent(position.lineNumber);
                            suggestion.insertText = suggestion.insertText.substring(
                                QUOTES.includes(lineContent.charAt(startOfWordColumn - 2)) ? 1 : 0,
                                suggestion.insertText.length - (QUOTES.includes(lineContent.charAt(endColumn - 1)) ? 1 : 0)
                            );
                        }

                        return suggestion;
                    })
                };
            }
        }))

        autoCompletionProviders.value.push(monaco.languages.registerCompletionItemProvider(["yaml", "plaintext"], {
            triggerCharacters: ["."],
            async provideCompletionItems(model, position) {
                const source = model.getValue();
                const parsed = YAML_UTILS.parse(source, false);

                const parentFieldMatcher = model.findPreviousMatch(RegexProvider.capturePebbleVarParent + "$", position, true, false, null, true);
                if (parentFieldMatcher === null || parentFieldMatcher.matches === null) {
                    return NO_SUGGESTIONS;
                }

                const startOfWordColumn = position.column - parentFieldMatcher.matches[2].length;
                return {
                    suggestions: (await yamlAutoCompletionProvider.nestedFieldAutoCompletion(source, parsed, parentFieldMatcher.matches[1]))
                        .map(s => propertySuggestion(s, {
                            lineNumber: position.lineNumber,
                            startColumn: startOfWordColumn,
                            endColumn: endOfWordColumn(position, model)
                        }))
                };
            }
        }));
    }

    /**
     * Goal of this method is to add an observer on the suggest widget to auto-resize it to fit tasks without ellipsis at first appearance.
     * It's using a MutationObserver. The observer expects two scenario:
     *
     *  - `target` is looked at. If it's a Sash (VSCode resizer handle) and it's not disabled (which is the case while loading schema),
     *  it manipulates it through MouseEvents to resize the suggest window. If it's disabled it returns and wait for the next pass while watching class changes
     *  - otherwise, addedNodes is looked at. In that case we are watching for any new children of the global vscode widget handler. The goal is to detect the sash addition
     *  because it's not there at startup. Once detected, if it's disabled it changes the observer to target the Sash (see above) but watching the class to detect `disabled` class removal.
     *  If the Sash is not disabled, we resize directly.
     *
     *  Once the resize has been done, the observer is disconnected and put back to undefined so that new instances of Monaco repeats the process to target the proper DOM element.
     */
    function observeAndResizeSuggestWidget() {
        if (suggestWidgetResizeObserver.value !== undefined) {
            return;
        }

        suggestWidgetResizeObserver.value = new MutationObserver(([{
            target,
            addedNodes
        }]) => {
            const simulateResizeOnSashAndDisconnect = (resizer: HTMLElement) => {
                suggestWidgetResizeObserver.value?.disconnect();
                suggestWidgetResizeObserver.value = undefined;

                const resizerInitialCoordinates = {
                    x: resizer.getBoundingClientRect().left,
                    y: resizer.getBoundingClientRect().top
                };

                resizer.dispatchEvent(new MouseEvent("mouseenter", {
                    bubbles: true,
                    clientX: resizerInitialCoordinates.x,
                    clientY: resizerInitialCoordinates.y
                }));
                resizer.dispatchEvent(new MouseEvent("mouseover", {
                    bubbles: true,
                    clientX: resizerInitialCoordinates.x,
                    clientY: resizerInitialCoordinates.y
                }));
                resizer.dispatchEvent(new MouseEvent("mousedown", {
                    bubbles: true,
                    clientX: resizerInitialCoordinates.x,
                    clientY: resizerInitialCoordinates.y
                }));
                resizer.dispatchEvent(new MouseEvent("mousemove", {
                    bubbles: true,
                    clientX: resizerInitialCoordinates.x + 80,
                    clientY: resizerInitialCoordinates.y
                }));
                resizer.dispatchEvent(new MouseEvent("mouseup", {
                    bubbles: true,
                    clientX: resizerInitialCoordinates.x + 80,
                    clientY: resizerInitialCoordinates.y
                }));
                resizer.dispatchEvent(new MouseEvent("mouseout", {
                    bubbles: true,
                    clientX: resizerInitialCoordinates.x + 80,
                    clientY: resizerInitialCoordinates.y
                }));
                resizer.dispatchEvent(new MouseEvent("mouseleave", {
                    bubbles: true,
                    clientX: resizerInitialCoordinates.x + 80,
                    clientY: resizerInitialCoordinates.y
                }));
            }

            const targetHtmlElement = target as HTMLElement;
            if (targetHtmlElement.classList.contains("monaco-sash")) {
                if (!targetHtmlElement.classList.contains("disabled")) {
                    simulateResizeOnSashAndDisconnect(targetHtmlElement);
                }

                return;
            }

            const maybeSuggestWidgetHtmlElement = addedNodes?.[0] as HTMLElement;
            if (maybeSuggestWidgetHtmlElement?.classList.contains("suggest-widget")) {
                suggestWidget.value = maybeSuggestWidgetHtmlElement;
                const resizer = maybeSuggestWidgetHtmlElement.querySelector(".monaco-sash.vertical") as HTMLElement;

                if (resizer.classList.contains("disabled")) {
                    suggestWidgetResizeObserver.value!.disconnect();
                    suggestWidgetResizeObserver.value?.observe(resizer, {attributeFilter: ["class"]})
                } else {
                    simulateResizeOnSashAndDisconnect(resizer);
                }
            }
        });

        const $el = editorRef.value
        if($el !== null) {
            suggestWidgetResizeObserver.value.observe($el.querySelector(".overflowingContentWidgets")!, {childList: true})
        }
    }

    async function initMonaco () {
        let options = {
            ...{
                value: props.value,
                theme: props.theme,
                language: props.language,
                suggest: {
                    showClasses: false,
                    showWords: false
                }
            },
            ...props.options
        };

        if (props.diffEditor) {
            if(editorRef.value){
                localDiffEditor = monaco.editor.createDiffEditor(editorRef.value, {
                    ...options,
                    ignoreTrimWhitespace: false
                });
                let originalModel = monaco.editor.createModel(props.original, props.language);
                let modifiedModel = monaco.editor.createModel(props.value, props.language);
                localDiffEditor.setModel({
                    original: originalModel,
                    modified: modifiedModel
                });
            }
        } else {
            monaco.editor.addKeybindingRule({
                keybinding: monaco.KeyMod.CtrlCmd | monaco.KeyCode.Space,
                command: "editor.action.triggerSuggest"
            })

            monaco.editor.addKeybindingRule({
                keybinding: monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyP,
                command: "editor.action.quickCommand"
            })

            monaco.editor.addKeybindingRule({
                keybinding: monaco.KeyMod.CtrlCmd | monaco.KeyCode.DownArrow,
                command: "editor.action.fontZoomOut",
                when: "editorFocus"
            })

            monaco.editor.addKeybindingRule({
                keybinding: monaco.KeyMod.CtrlCmd | monaco.KeyCode.UpArrow,
                command: "editor.action.fontZoomIn",
                when: "editorFocus"
            })

            monaco.editor.addKeybindingRule({
                keybinding: monaco.KeyMod.CtrlCmd | monaco.KeyCode.Digit0,
                command: "editor.action.fontZoomReset",
                when: "editorFocus"
            });

            if(editorRef.value){
                localEditor = monaco.editor.create(editorRef.value, options);
            }

            if (!props.input) {
                await changeTab(props.path, () => Promise.resolve(props.value), false);
            }
        }

        observeAndResizeSuggestWidget();

        let _editor = getModifiedEditor();
        _editor?.onDidChangeModelContent(function (event) {
            let value = _editor.getValue();

            if (props.value !== value) {
                emit("change", value, event);

                if (!props.input && current.value && current.value.name) {
                    store.commit("editor/setTabDirty", {
                        ...current.value,
                        dirty: true,
                    });
                }
            }
        });

        setTimeout(() => monaco.editor.remeasureFonts(), 1)
        emit("editorDidMount", editorResolved.value);
    }

    const current = computed(() => {
        return store.state.editor.current;
    });

    async function changeTab(pathOrName: string, valueSupplier: () => Promise<string>, useModelCache = true) {
        let model;
        const prefix = props.schemaType ? `${props.schemaType}-` : "";
        if (props.input || pathOrName === undefined) {
            model = monaco.editor.createModel(
                await valueSupplier(),
                props.language,
                monaco.Uri.file(prefix + Utils.uid() + (props.language ? `.${props.language}` : ""))
            );
        } else {
            if (!pathOrName.includes(".") && props.language) {
                pathOrName = `${pathOrName}.${props.language}`;
            }
            const fileUri = monaco.Uri.file(prefix + pathOrName);
            model = monaco.editor.getModel(fileUri);
            if (model === null) {
                model = monaco.editor.createModel(
                    await valueSupplier(),
                    props.language,
                    fileUri
                );
            } else if (!useModelCache) {
                model.setValue(await valueSupplier());
            }
        }
        localEditor?.setModel(model);

        return model
    }

    function getModifiedEditor() {
        return props.diffEditor ? localDiffEditor?.getModifiedEditor() : localEditor;
    }

    function getOriginalEditor() {
        return props.diffEditor ? localDiffEditor?.getOriginalEditor() : localEditor;
    }

    function focus() {
        editorResolved.value?.focus();
    }

    function destroy() {
        disposeObservers();
        autoCompletionProviders.value.forEach(provider => provider.dispose());
        if(props.diffEditor) {
            localDiffEditor?.getModel()?.modified?.dispose();
            localDiffEditor?.getModel()?.original?.dispose();
            localDiffEditor?.dispose();
        } else {
            localEditor?.getModel()?.dispose();
            localEditor?.dispose();
        }
    }

    function needReload(newValue?: { renderSideBySide?: boolean }, oldValue?: { renderSideBySide?: boolean }) {
        return oldValue?.renderSideBySide !== newValue?.renderSideBySide;
    }

    function reload() {
        destroy();
        initMonaco();
    };
</script>

<style scoped lang="scss">
.ks-monaco-editor {
    position: absolute;
    width: 100%;
    height: 100%;
    outline: none;
}

.main-editor>#editorWrapper .monaco-editor {
    padding: 1rem 0 0 1rem;
}
</style>

<style lang="scss">
@import "../../styles/layout/root-dark";

    .custom-dark-vs-theme .ks-monaco-editor .sticky-widget {
        background-color: $input-bg;
    }

    .monaco-editor {
        .monaco-scrollable-element {
            > .scrollbar {
                .slider {
                    width: 13px !important;
                    background: var(--ks-border-primary) !important;
                    border-radius: 8px !important;
                    border: 4px solid var(--ks-background-body) !important;
                }
            }
        }
    }
</style>
