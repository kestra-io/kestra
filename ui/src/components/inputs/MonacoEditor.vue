<template>
    <div class="ks-monaco-editor" />
</template>

<script lang="ts">
    import {defineComponent} from "vue";
    import {mapMutations, mapState} from "vuex";

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
    import Utils from "../../utils/utils";
    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";
    import {QUOTE, YamlNoAutoCompletion} from "../../services/autoCompletionProvider.js"
    import {FlowAutoCompletion} from "override/services/flowAutoCompletionProvider.js";
    import RegexProvider from "../../utils/regex";
    import type {Position} from "monaco-editor"

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

    export default defineComponent({
        data() {
            return {
                flowsInputsCache: {},
                autoCompletionProviders: [] as monaco.IDisposable[],
                monaco: null as typeof monaco | null,
            }
        },
        computed: {
            ...mapState("namespace", ["datatypeNamespaces"]),
            ...mapState("core", ["monacoYamlConfigured"]),
            ...mapState("editor", ["current"]),
            prefix() {
                return this.schemaType ? `${this.schemaType}-` : "";
            },
        },

        props: {
            path: {
                type: String,
                default: "",
            },
            original: {
                type: String,
                default: "",
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
            path(newValue, oldValue) {
                if (newValue !== oldValue) {
                    this.changeTab(newValue, () => Promise.resolve(this.value));
                }
            },
            options: {
                deep: true,
                handler: function (newValue, oldValue) {
                    if (this.$options.editor && this.needReload(newValue, oldValue)) {
                        this.reload();
                    } else {
                        this.$options.editor.updateOptions(newValue);
                    }
                }
            },
            value: function (newValue) {
                if (this.$options.editor) {
                    let editor = this.getModifiedEditor();

                    if (newValue !== editor.getValue()) {
                        editor.setValue(newValue);
                    }
                }
            },
            original: function (newValue) {
                if (this.$options.editor && this.diffEditor) {
                    let editor = this.getOriginalEditor();

                    if (newValue !== editor.getValue()) {
                        editor.setValue(newValue);
                    }
                }
            },
            theme: function (newVal) {
                if (this.$options.editor) {
                    monaco.editor.setTheme(newVal);
                }
            }
        },
        mounted: async function () {
            // assign monaco so that it gets available outside of monacoeditor
            this.monaco = monaco;
            await document.fonts.ready.then(() => {
                this.initMonaco()
            })

            if (!this.monacoYamlConfigured && this.language === "yaml") {
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

            const QUOTES = ["\"", "'"];
            const endOfWordColumn = (position, model) => {
                return position.column + (model.findNextMatch(RegexProvider.beforeSeparator(QUOTES), position, true, false, null, true)?.matches[0].length ?? 0);
            }

            this.autoCompletionProviders.push(monaco.languages.registerCompletionItemProvider("yaml", {
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

            const propertySuggestion = (value: string, position: Position, kind?: monaco.languages.CompletionItemKind) => {
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
                    const parsed = YAML_UTILS.parse(source, false);

                    const functionMatcher = model.findPreviousMatch(RegexProvider.capturePebbleFunction + "$", position, true, false, null, true);
                    if (functionMatcher === null) {
                        return NO_SUGGESTIONS;
                    }

                    const wordStartOffset = functionMatcher.matches?.[3]?.length
                        ?? model.findPreviousMatch(RegexProvider.beforeSeparator(QUOTES) + "$", position, true, false, null, true).matches[0].length;
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

            this.autoCompletionProviders.push(monaco.languages.registerCompletionItemProvider(["yaml", "plaintext"], {
                triggerCharacters: ["."],
                async provideCompletionItems(model, position) {
                    const source = model.getValue();
                    const parsed = YAML_UTILS.parse(source, false);

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
            (window as any).pasteToEditor = (textToPaste:string) => {
                this.$options.editor.executeEdits("", [{range: this.$options.editor.getSelection(), text: textToPaste}])
            };
            (window as any).clearEditor = () => {
                this.$options.editor.getModel().setValue("")
            };
        },
        beforeUnmount: function () {
            this.destroy();
        },
        methods: {
            ...mapMutations("editor", ["setTabDirty"]),
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
                    this.$options.editor = monaco.editor.createDiffEditor(this.$el, {...options, ignoreTrimWhitespace: false});
                    let originalModel = monaco.editor.createModel(this.original, this.language);
                    let modifiedModel = monaco.editor.createModel(this.value, this.language);
                    this.$options.editor.setModel({
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

                    this.$options.editor = monaco.editor.create(this.$el, options);

                    if(!this.input){
                        await this.changeTab(this.path, () => Promise.resolve(this.value), false);
                    }
                }

                let editor: monaco.editor.IStandaloneCodeEditor = this.getModifiedEditor();
                editor.onDidChangeModelContent(function (event) {
                    let value = editor.getValue();

                    if (self.value !== value) {
                        self.$emit("change", value, event);

                        if (!self.input && self.current && self.current.name) {
                            self.setTabDirty({
                                ...self.current,
                                dirty: true,
                            });
                        }
                    }
                });

                setTimeout(() => monaco.editor.remeasureFonts(), 1)
                this.$emit("editorDidMount", this.$options.editor);
            },
            async changeTab(pathOrName:string, valueSupplier: () => Promise<string>, useModelCache = true) {
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
                this.$options.editor.setModel(model);

                return model
            },
            getModifiedEditor: function () {
                return this.diffEditor ? this.$options.editor.getModifiedEditor() : this.$options.editor;
            },
            getOriginalEditor: function () {
                return this.diffEditor ? this.$options.editor.getOriginalEditor() : this.$options.editor;
            },
            focus: function () {
                this.$options.editor.focus();
            },
            destroy: function () {
                this.autoCompletionProviders.forEach(provider => provider.dispose());
                this.$options.editor?.getModel()?.dispose?.();
                this.$options.editor?.dispose?.();
            },
            needReload: function (newValue: {renderSideBySide: boolean}, oldValue: {renderSideBySide: boolean}) {
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
