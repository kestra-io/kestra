<template>
    <div class="ks-monaco-editor" />
</template>

<script lang="ts">
    import {defineComponent} from "vue";
    import {mapActions, mapMutations, mapState} from "vuex";

    import "monaco-editor/esm/vs/editor/editor.all.js";
    import "monaco-editor/esm/vs/editor/standalone/browser/iPadShowKeyboard/iPadShowKeyboard.js";
    import "monaco-editor/esm/vs/editor/standalone/browser/quickAccess/standaloneCommandsQuickAccess.js"
    import "monaco-editor/esm/vs/language/json/monaco.contribution";
    import "monaco-editor/esm/vs/basic-languages/monaco.contribution";
    import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
    import EditorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
    import YamlWorker from "./yaml.worker.js?worker";
    import JsonWorker from "monaco-editor/esm/vs/language/json/json.worker?worker";
    import {configureMonacoYaml} from "monaco-yaml";
    import {yamlSchemas} from "override/utils/yamlSchemas";
    import {editorViewTypes} from "../../utils/constants";
    import Utils from "../../utils/utils";
    import YamlUtils from "../../utils/yamlUtils";
    import {QUOTE, YamlNoAutoCompletion} from "../../services/autoCompletionProvider.js"
    import {FlowAutoCompletion} from "override/services/flowAutoCompletionProvider.js";
    import RegexProvider from "../../utils/regex";
    import type {Position} from "monaco-editor"

    window.MonacoEnvironment = {
        getWorker(moduleId, label) {
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
        rules: [{background: "161822"}],
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

    export default defineComponent({
        data() {
            return {
                flowsInputsCache: {},
                autoCompletionProviders: []
            }
        },
        computed: {
            ...mapState("namespace", ["datatypeNamespaces"]),
            ...mapState("core", ["autocompletionSource", "monacoYamlConfigured"]),
            ...mapState({
                currentTab: (state) => state.editor.current,
                tabs: (state) => state.editor.tabs,
                flow: (state) => state.flow.flow,
                view: (state) => state.editor.view
            }),
            prefix() {
                return this.schemaType ? `${this.schemaType}-` : "";
            }
        },
        props: {
            original: {
                type: String,
                default: undefined
            },
            value: {
                type: String,
                required: true
            },
            theme: {
                type: String,
                default: "light"
            },
            language: {
                type: String,
                default: undefined
            },
            extension: {
                type: String,
                default: undefined
            },
            options: {
                type: Object,
                default: undefined
            },
            schemaType: {
                type: String,
                default: undefined
            },
            diffEditor: {
                type: Boolean,
                default: false
            },
            input: {
                type: Boolean,
                default: false
            },
            creating: {
                type: Boolean,
                default: false
            }
        },
        emits: ["editorDidMount", "change", "tabLoaded"],
        model: {
            event: "change"
        },
        watch: {
            tabs(newValue, oldValue) {
                if (newValue?.length < oldValue?.length) {
                    const openedTabPaths = newValue.map(tab => (tab.path ?? tab.name));
                    monaco.editor?.getModels().filter(model => {
                        return !openedTabPaths.includes(model.uri?.path.substring(this.prefix.length + 1));
                    }).forEach(model => {
                        model.dispose();
                    });
                }
            },
            async currentTab(newValue, oldValue) {
                if (!newValue) return;

                const newTabName = (newValue.path ?? newValue.name);
                // Tab hasn't changed, it's probably only the dirty flag that changed
                if (newTabName === (oldValue?.path ?? oldValue?.name)) {
                    return;
                }

                let model
                if (newValue.persistent && this.flow?.source) {
                    model = await this.changeTab("Flow", () => this.flow.source);
                } else {
                    const payload = {
                        namespace: this.$route.params.namespace || this.$route.params.id,
                        path: newValue.path ?? newValue.name,
                    };

                    model = await this.changeTab(newTabName, () => this.readFile(payload));
                }
                const source = model.getValue()
                this.$emit("change", source);
                this.$emit("tabLoaded", newValue, source);
            },
            options: {
                deep: true,
                handler: function (newValue, oldValue) {
                    if (this.editor && this.needReload(newValue, oldValue)) {
                        this.reload();
                    } else {
                        this.editor.updateOptions(newValue);
                    }
                }
            },
            value: function (newValue) {
                if (this.editor) {
                    let editor = this.getModifiedEditor();

                    if (newValue !== editor.getValue()) {
                        editor.setValue(newValue);
                    }
                }
            },
            original: function (newValue) {
                if (this.editor && this.diffEditor) {
                    let editor = this.getOriginalEditor();

                    if (newValue !== editor.getValue()) {
                        editor.setValue(newValue);
                    }
                }
            },
            theme: function (newVal) {
                if (this.editor) {
                    monaco.editor.setTheme(newVal);
                }
            }
        },
        mounted: async function () {
            this.monaco = monaco;
            await document.fonts.ready.then(() => {
                this.initMonaco(monaco)
            })

            if (!this.monacoYamlConfigured && (this.creating || this.currentTab?.flow)) {
                this.$store.commit("core/setMonacoYamlConfigured", true);
                configureMonacoYaml(monaco, {
                    enableSchemaRequest: true,
                    hover: true,
                    completion: true,
                    validate: true,
                    format: true,
                    schemas: yamlSchemas(this.$store)
                });
            }

            const NO_SUGGESTIONS = {suggestions: []};

            let yamlAutoCompletionProvider: YamlNoAutoCompletion;
            if (this.schemaType === "flow") {
                yamlAutoCompletionProvider = new FlowAutoCompletion(this.$store);
            } else {
                yamlAutoCompletionProvider = new YamlNoAutoCompletion();
            }

            const endOfWordColumn = (position, model) => {
                return position.column + (model.findNextMatch(RegexProvider.beforeSeparator(), position, true, false, null, true)?.matches[0].length ?? 0);
            }

            this.autoCompletionProviders.push(monaco.languages.registerCompletionItemProvider("yaml", {
                triggerCharacters: [":"],
                async provideCompletionItems(model, position) {
                    const source = model.getValue();
                    const cursorPosition = model.getOffsetAt(position);
                    const parsed = YamlUtils.parse(source, false);

                    const currentWord = model.findPreviousMatch(RegexProvider.beforeSeparator(), position, true, false, null, true);
                    const elementUnderCursor = YamlUtils.localizeElementAtIndex(source, cursorPosition);
                    if (elementUnderCursor?.key === undefined) {
                        return NO_SUGGESTIONS;
                    }

                    const parentStartLine = model.getPositionAt(elementUnderCursor.range[0]).lineNumber;
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
                                    startColumn: position.column - currentWord.matches[0].length,
                                    endColumn: endColumn
                                }
                            });
                        })
                    };
                }
            }));

            const propertySuggestion = (value: string, position: Position, kind: monaco.languages.CompletionItemKind | undefined) => {
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

            this.autoCompletionProviders.push(monaco.languages.registerCompletionItemProvider(["yaml", "plaintext"], {
                triggerCharacters: ["{"],
                async provideCompletionItems(model, position) {
                    // Not a subfield access
                    const rootPebbleVariableMatcher = model.findPreviousMatch(RegexProvider.capturePebbleVarRoot + "$", position, true, false, null, true);
                    if (rootPebbleVariableMatcher === null) {
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

            this.autoCompletionProviders.push(monaco.languages.registerCompletionItemProvider(["yaml", "plaintext"], {
                triggerCharacters: ["("],
                async provideCompletionItems(model, position) {
                    const source = model.getValue();
                    const parsed = YamlUtils.parse(source, false);

                    const functionMatcher = model.findPreviousMatch(RegexProvider.capturePebbleFunction + "$", position, true, false, null, true);
                    if (functionMatcher === null) {
                        return NO_SUGGESTIONS;
                    }

                    const QUOTES = ["\"", "'"];
                    const wordStartOffset = functionMatcher.matches?.[3]?.length
                        ?? model.findPreviousMatch(RegexProvider.beforeSeparator(QUOTES) + "$", position, true, false, null, true).matches[0].length;
                    const startOfWordColumn = position.column - wordStartOffset;
                    return {
                        suggestions: (await yamlAutoCompletionProvider.functionAutoCompletion(
                            parsed,
                            functionMatcher.matches[1],
                            Object.fromEntries(functionMatcher.matches?.[2]?.split(/ *, */)?.map(arg => arg.split(/ *= */)) ?? []))
                        ).map(s => {
                            const suggestion = propertySuggestion(s, {
                                lineNumber: position.lineNumber,
                                startColumn: startOfWordColumn,
                                endColumn: endOfWordColumn(position, model)
                            }, monaco.languages.CompletionItemKind.Value);

                            // If the inserted value is a string (surrounded by quotes), we remove them if there is already one
                            if (suggestion.insertText.startsWith(QUOTE) && suggestion.insertText.endsWith(QUOTE)) {
                                const lineContent = model.getLineContent(position.lineNumber);
                                suggestion.insertText = suggestion.insertText.substring(
                                    QUOTES.includes(lineContent.charAt(startOfWordColumn - 2)) ? 1 : 0,
                                    suggestion.insertText.length - (QUOTES.includes(lineContent.charAt(endOfWordColumn)) ? 1 : 0)
                                );
                            }

                            return suggestion;
                        })
                    };
                }
            }))

            this.autoCompletionProviders.push(monaco.languages.registerCompletionItemProvider(["yaml", "plaintext"], {
                triggerCharacters: ["."],
                async provideCompletionItems(model, position) {
                    const source = model.getValue();
                    const parsed = YamlUtils.parse(source, false);

                    const parentFieldMatcher = model.findPreviousMatch(RegexProvider.capturePebbleVarParent + "$", position, true, false, null, true);
                    if (parentFieldMatcher === null) {
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

            // Exposing functions globally for testing purposes
            window.pasteToEditor = (textToPaste) => {
                this.editor.executeEdits("", [{range: this.editor.getSelection(), text: textToPaste}])
            };
            window.clearEditor = () => {
                this.editor.getModel().setValue("")
            };
        },
        beforeUnmount: function () {
            this.destroy();
        },
        methods: {
            ...mapMutations("editor", ["changeOpenedTabs"]),
            ...mapActions("namespace", ["readFile"]),
            initMonaco: async function () {
                let self = this;
                let options = {
                    ...{
                        value: this.value,
                        theme: this.theme,
                        language: this.language,
                        suggest: {
                            showClasses: false,
                            showWords: false
                        }
                    },
                    ...this.options
                };

                if (this.diffEditor) {
                    this.editor = monaco.editor.createDiffEditor(this.$el, {...options, ignoreTrimWhitespace: false});
                    let originalModel = monaco.editor.createModel(this.original, this.language);
                    let modifiedModel = monaco.editor.createModel(this.value, this.language);
                    this.editor.setModel({
                        original: originalModel,
                        modified: modifiedModel
                    });
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

                    this.editor = monaco.editor.create(this.$el, options);

                    if (!this.input) {
                        const name = this.currentTab?.path ?? this.currentTab?.name;
                        const value = this.currentTab?.flow || this.creating ? this.value : this.readFile({
                            namespace: this.$route.params.namespace || this.$route.params.id,
                            path: name
                        })

                        await this.changeTab(name, () => value, false);
                    }
                }

                let editor = this.getModifiedEditor();
                editor.onDidChangeModelContent(function (event) {
                    let value = editor.getValue();

                    if (self.value !== value) {
                        self.$emit("change", value, event);

                        if (!self.input && self.currentTab && self.currentTab.name) {
                            self.changeOpenedTabs({
                                action: "dirty",
                                ...self.currentTab,
                                dirty: true,
                            });
                        }
                    }
                });

                setTimeout(() => monaco.editor.remeasureFonts(), 1)
                this.$emit("editorDidMount", this.editor);
            },
            async changeTab(pathOrName, valueSupplier, useModelCache = true) {
                let model;
                if (this.input || pathOrName === undefined) {
                    model = monaco.editor.createModel(
                        await valueSupplier(),
                        this.language,
                        monaco.Uri.file(this.prefix + Utils.uid() + (this.language ? `.${this.language}` : ""))
                    );
                } else {
                    if (!pathOrName.includes(".") && this.language) {
                        pathOrName = `${pathOrName}.${this.language}`;
                    }
                    const fileUri = monaco.Uri.file(this.prefix + pathOrName);
                    model = monaco.editor.getModel(fileUri);
                    if (model === null) {
                        model = monaco.editor.createModel(
                            await valueSupplier(),
                            this.language,
                            fileUri
                        );
                    } else if (!useModelCache) {
                        model.setValue(await valueSupplier());
                    }
                }
                this.editor.setModel(model);

                return model
            },
            getEditor: function () {
                return this.editor;
            },
            getModifiedEditor: function () {
                return this.diffEditor ? this.editor.getModifiedEditor() : this.editor;
            },
            getOriginalEditor: function () {
                return this.diffEditor ? this.editor.getOriginalEditor() : this.editor;
            },
            focus: function () {
                this.editor.focus();
            },
            destroy: function () {
                if (this.view === editorViewTypes.TOPOLOGY) return;

                this.autoCompletionProviders.forEach(provider => provider.dispose());
                this.editor?.getModel()?.dispose?.();
                this.editor?.dispose?.();
            },
            needReload: function (newValue, oldValue) {
                return oldValue.renderSideBySide !== newValue.renderSideBySide;
            },
            reload: function () {
                this.destroy();
                this.initMonaco();
            },
        },
    });
</script>

<style scoped lang="scss">
    .ks-monaco-editor {
        position: absolute;
        overflow-x: scroll;
        width: 100%;
        height: 100%;
        outline: none;
    }

    .main-editor > #editorWrapper .monaco-editor {
        padding: 1rem 0 0 1rem;
    }
</style>

<style lang="scss">
    @import "../../styles/layout/root-dark";

    .custom-dark-vs-theme .ks-monaco-editor .sticky-widget {
        background-color: $input-bg;
    }
</style>
