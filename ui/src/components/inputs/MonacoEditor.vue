<template>
    <div class="ks-monaco-editor" />
</template>

<script>
    import {defineComponent} from "vue";
    import {mapState, mapMutations, mapActions} from "vuex";

    import "monaco-editor/esm/vs/editor/editor.all.js";
    import "monaco-editor/esm/vs/editor/standalone/browser/iPadShowKeyboard/iPadShowKeyboard.js";
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
    import uniqBy from "lodash/uniqBy";

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

    export default defineComponent({
        data() {
            return {
                flowsInputsCache: {}
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
                default: "vs"
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
        emits: ["editorDidMount", "change"],
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

                if (newValue.persistent && this.flow?.source) {
                    await this.changeTab("Flow", () => this.flow.source);
                } else {
                    const payload = {
                        namespace: this.$route.params.namespace || this.$route.params.id,
                        path: newValue.path ?? newValue.name,
                    };

                    await this.changeTab(newTabName, () => this.readFile(payload));
                }
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
            let _this = this;

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

            const noSuggestions = {suggestions: []};
            if (this.schemaType === "flow") {
                // If we have an autocompletion source, it means we don't have the full model so we won't be able to provide cursor-based autocompletion
                this.subflowAutocompletionProvider = monaco.languages.registerCompletionItemProvider("yaml", {
                    triggerCharacters: [":"],
                    async provideCompletionItems(model, position) {
                        if (_this.schemaType !== "flow") {
                            return noSuggestions;
                        }

                        const namespaceAutocompletion = await _this.namespaceAutocompletion(model, position);
                        if (namespaceAutocompletion) {
                            return namespaceAutocompletion;
                        }

                        const flowIdAutocompletion = await _this.flowIdAutocompletion(model, position);
                        if (flowIdAutocompletion) {
                            return flowIdAutocompletion;
                        }

                        const subflowInputsAutocompletion = await _this.subflowInputsAutocompletion(model, position);
                        if (subflowInputsAutocompletion) {
                            return subflowInputsAutocompletion;
                        }

                        return noSuggestions;
                    }
                })

                this.pebbleAutocompletion = monaco.languages.registerCompletionItemProvider(["yaml", "plaintext"], {
                    triggerCharacters: ["{"],
                    provideCompletionItems(model, position) {
                        const lineContent = _this.lineContent(model, position);
                        const tillCursorContent = _this.tillCursorContent(lineContent, position);
                        const match = tillCursorContent.match(/\{\{ *(?:.*~ ?)?$/);
                        if (!match) {
                            return noSuggestions;
                        }

                        const suggestionFor = (label) => ({
                            kind: monaco.languages.CompletionItemKind.Property,
                            label,
                            insertText: label,
                            range: {
                                startLineNumber: position.lineNumber,
                                endLineNumber: position.lineNumber,
                                startColumn: position.column,
                                endColumn: position.column
                            }
                        });
                        return {
                            suggestions: [
                                suggestionFor("outputs"),
                                suggestionFor("inputs"),
                                suggestionFor("vars"),
                                suggestionFor("flow"),
                                suggestionFor("execution"),
                                suggestionFor("trigger"),
                                suggestionFor("task"),
                                suggestionFor("taskrun"),
                                suggestionFor("labels"),
                                suggestionFor("envs"),
                                suggestionFor("globals"),
                                suggestionFor("parents"),
                                suggestionFor("error")
                            ]
                        };
                    }
                });

                this.nestedFieldAutocompletionProvider = monaco.languages.registerCompletionItemProvider(["yaml", "plaintext"], {
                    triggerCharacters: ["."],
                    async provideCompletionItems(model, position) {
                        const lineContent = _this.lineContent(model, position);
                        const tillCursorContent = _this.tillCursorContent(lineContent, position);
                        const match = tillCursorContent.match(/( *([^{ ]*)\.)([^.} ]*)$/);
                        if (!match) {
                            return noSuggestions;
                        }

                        let nextDotIndex;
                        // We're at the end of the line
                        if (lineContent.length === tillCursorContent.length) {
                            nextDotIndex = tillCursorContent.length;
                        } else {
                            const remainingLineText = lineContent.substring(tillCursorContent.length);
                            const nextDotMatcher = remainingLineText.match(/[ .}]/);
                            if (!nextDotMatcher) {
                                nextDotIndex = lineContent.length - 1;
                            } else {
                                nextDotIndex = tillCursorContent.length + nextDotMatcher.index;
                            }
                        }

                        const indexOfFieldToComplete = match.index + match[1].length;
                        return {
                            suggestions: await _this.autocompletionForField(
                                _this.autocompletionSource,
                                lineContent,
                                match[2],
                                match[3],
                                position.lineNumber,
                                [indexOfFieldToComplete, nextDotIndex]
                            )
                        };
                    }
                })
            }

            // Exposing functions globally for testing purposes
            window.pasteToEditor = (textToPaste) => {this.editor.executeEdits("", [{range: this.editor.getSelection(), text: textToPaste}])};
            window.clearEditor = () => {this.editor.getModel().setValue("")};
        },
        beforeUnmount: function () {
            this.destroy();
        },
        methods: {
            ...mapMutations("editor", ["changeOpenedTabs"]),
            ...mapActions("namespace", ["readFile"]),
            async namespaceAutocompletion(model, position) {
                const lineContent = this.lineContent(model, position);
                const match = this.tillCursorContent(lineContent, position).match(/^( *namespace:( *))(.*)$/);
                if (!match) {
                    return undefined;
                }

                const indexOfFieldToComplete = match.index + match[1].length;
                if (!this.datatypeNamespaces) {
                    await this.$store.dispatch("namespace/loadNamespacesForDatatype", {dataType: "flow"})
                }
                let filteredNamespaces = this.datatypeNamespaces;
                if (match[3].length > 0) {
                    filteredNamespaces = filteredNamespaces.filter(n => n.startsWith(match[3]));
                }
                return {
                    suggestions: filteredNamespaces.map(namespace => ({
                        kind: monaco.languages.CompletionItemKind.Value,
                        label: namespace,
                        insertText: (match[2].length > 0 ? "" : " ") + namespace,
                        range: {
                            startLineNumber: position.lineNumber,
                            endLineNumber: position.lineNumber,
                            startColumn: indexOfFieldToComplete + 1,
                            endColumn: YamlUtils.nextDelimiterIndex(lineContent, position.column - 1)
                        }
                    }))
                };
            },
            async flowIdAutocompletion(model, position) {
                const lineContent = this.lineContent(model, position);
                const match = this.tillCursorContent(lineContent, position).match(/^( *flowId:( *))(.*)$/);
                if (!match) {
                    return undefined;
                }

                const indexOfFieldToComplete = match.index + match[1].length;

                const source = model.getValue();
                const namespacesWithRange = YamlUtils.extractFieldFromMaps(source, "namespace").reverse();
                const namespace = namespacesWithRange.find(namespaceWithRange => {
                    const range = namespaceWithRange.range;
                    const offset = model.getOffsetAt(position)
                    return range[0] <= offset && offset <= range[2];
                })?.namespace;
                if (namespace === undefined) {
                    return undefined;
                }

                const flowAsJs = YamlUtils.parse(source);
                let flowIds = (await this.$store.dispatch("flow/flowsByNamespace", namespace))
                    .map(flow => flow.id)
                if (match[3].length > 0) {
                    flowIds = flowIds.filter(flowId => flowId.startsWith(match[3]));
                }
                if (flowAsJs?.id && flowAsJs?.namespace === namespace) {
                    flowIds = flowIds.filter(flowId => flowId !== flowAsJs?.id);
                }

                return {
                    suggestions: flowIds.map(flowId => ({
                        kind: monaco.languages.CompletionItemKind.Value,
                        label: flowId,
                        insertText: (match[2].length > 0 ? "" : " ") + flowId,
                        range: {
                            startLineNumber: position.lineNumber,
                            endLineNumber: position.lineNumber,
                            startColumn: indexOfFieldToComplete + 1,
                            endColumn: YamlUtils.nextDelimiterIndex(lineContent, position.column - 1)
                        }
                    }))
                };
            },
            async subflowInputsAutocompletion(model, position) {
                const subflowsWithRange = YamlUtils.extractMaps(model.getValue(), {
                    namespace: {populated: true},
                    flowId: {populated: true},
                    inputs: {present: true}
                });

                const previousWordCharWithInputsCapture = model.findPreviousMatch(
                    "(inputs)?([\\w:])",
                    position,
                    true,
                    false,
                    null,
                    true
                );
                if (!previousWordCharWithInputsCapture) {
                    return undefined;
                }

                const previousWordOffset = model.getOffsetAt({
                    column: previousWordCharWithInputsCapture.range.startColumn,
                    lineNumber: previousWordCharWithInputsCapture.range.startLineNumber,
                });

                let prefixAtPosition = model.getWordUntilPosition(position);
                if (prefixAtPosition?.word === "") {
                    prefixAtPosition = null;
                }
                const wordAtPosition = model.getWordAtPosition(position);
                const subflowTaskWithRange = subflowsWithRange
                    .reverse()
                    .find((subflowWithRange) => {
                        const range = subflowWithRange.range;
                        return (
                            range[0] <= previousWordOffset &&
                            previousWordOffset <= range[2]
                        );
                    });

                const subflowTask = subflowTaskWithRange?.map;
                if (!subflowTask) {
                    return undefined;
                }

                const subflowUid = subflowTask.namespace + "." + subflowTask.flowId;
                if (!this.flowsInputsCache[subflowUid]) {
                    try {
                        this.flowsInputsCache[subflowUid] = (await this.$store.dispatch(
                            "flow/loadFlow",
                            {
                                namespace: subflowTask.namespace,
                                id: subflowTask.flowId,
                                revision: subflowTask.revision,
                                source: false,
                                store: false,
                                deleted: true
                            }
                        )).inputs?.map(input => input.id) ?? [];
                    } catch {
                        return undefined;
                    }
                }

                let flowInputs = this.flowsInputsCache[subflowUid].filter(input => subflowTask.inputs?.[input] === undefined);
                if (prefixAtPosition?.word) {
                    flowInputs = flowInputs.filter(input => input.startsWith(prefixAtPosition.word));
                }

                let preInsertText = "";
                // We don't have any word under cursor but we're on the same line as the previous word => We must add a newline
                if (!wordAtPosition && previousWordCharWithInputsCapture?.range?.endLineNumber === position.lineNumber) {
                    preInsertText = "\n";
                    // By default, the new line will respect the parent indent. The only border case is when being on the same line as the expected parent (inputs), we must add manually the child indent
                    if (previousWordCharWithInputsCapture.matches[1]) {
                        preInsertText += "  ";
                    } else if (previousWordCharWithInputsCapture.matches[2] === ":") {
                        // User is filling an input value
                        return undefined;
                    }
                }

                return {
                    suggestions: flowInputs.map(input => {
                        const insertText = input + ": ";
                        return {
                            kind: monaco.languages.CompletionItemKind.Value,
                            label: input,
                            insertText: preInsertText + insertText,
                            range: {
                                startLineNumber: position.lineNumber,
                                endLineNumber: position.lineNumber,
                                startColumn: wordAtPosition?.startColumn ?? position.column,
                                endColumn: wordAtPosition?.endColumn ?? position.column
                            }
                        };
                    })
                }
            },
            lineContent(model, position) {
                return model.getValueInRange({
                    startLineNumber: position.lineNumber,
                    startColumn: 1,
                    endLineNumber: position.lineNumber,
                    endColumn: model.getLineMaxColumn(position.lineNumber)
                });
            },
            tillCursorContent(lineContent, position) {
                return lineContent.substring(0, position.column - 1);
            },
            tasks(source) {
                const tasksFromTasksProp = YamlUtils.extractFieldFromMaps(source, "tasks")
                    .flatMap(allTasks => allTasks.tasks);
                const tasksFromTaskProp = YamlUtils.extractFieldFromMaps(source, "task")
                    .map(task => task.task)
                    .flatMap(task => YamlUtils.pairsToMap(task) ?? [])

                return [...tasksFromTasksProp, ...tasksFromTaskProp]
                    .filter(task => typeof task?.get === "function" && task?.get("id"));
            },
            async autocompletionForField(
                source,
                lineContent,
                field,
                rest,
                lineNumber,
                fieldToCompleteIndexes
            ) {
                const flowAsJs = YamlUtils.parse(source);
                let autocompletions;
                switch (field) {
                case "inputs":
                    autocompletions = flowAsJs?.inputs?.map(input => input.id);
                    break;
                case "outputs":
                    autocompletions = this.tasks(source).map(task => task.get("id"));
                    break;
                case "labels":
                    autocompletions = Object.keys(flowAsJs?.labels ?? {});
                    break;
                case "flow":
                    autocompletions = ["id", "namespace", "revision", "tenantId"];
                    break;
                case "execution":
                    autocompletions = ["id", "startDate", "originalId"];
                    break;
                case "vars":
                    autocompletions = Object.keys(flowAsJs?.variables ?? {});
                    break;
                case "trigger":
                    autocompletions = await this.triggerVars(flowAsJs);
                    break;
                case "task":
                    autocompletions = ["id", "type"];
                    break;
                case "taskrun":
                    autocompletions = ["id", "startDate", "attemptsCount", "parentId", "value", "iteration"];
                    break;
                case "error":
                    autocompletions = ["taskId", "message", "stackTrace"];
                    break;
                default: {
                    let match = field.match(/^outputs\.([^.]+)$/);
                    if (match) {
                        autocompletions = await this.outputsFor(match[1], source);
                    }
                }
                }

                return autocompletions?.filter(autocomplete => rest ? autocomplete.startsWith(rest) : true)
                    ?.map(value => {
                        let endColumn = fieldToCompleteIndexes[1] + 1;
                        const endsWithDot = value.endsWith(".");
                        if (endsWithDot && lineContent.at(endColumn - 1) === ".") {
                            endColumn++;
                        }
                        return {
                            kind: monaco.languages.CompletionItemKind.Field,
                            label: endsWithDot ? value.substring(0, value.length - 1) : value,
                            insertText: value,
                            range: {
                                startLineNumber: lineNumber,
                                endLineNumber: lineNumber,
                                startColumn: fieldToCompleteIndexes[0] + 1,
                                endColumn: endColumn
                            }
                        }
                    }) ?? [];
            },
            async outputsFor(taskId, source) {
                const taskType = this.tasks(source).filter(task => task.get("id") === taskId)
                    .map(task => task.get("type"))
                    ?.[0];
                if (!taskType) {
                    return [];
                }

                const pluginDoc = await this.$store.dispatch("plugin/load", {cls: taskType, commit: false});

                return Object.keys(pluginDoc?.schema?.outputs?.properties ?? {});
            },
            async triggerVars(flowAsJs) {
                const fetchTriggerVarsByType = await Promise.all(
                    uniqBy(flowAsJs?.triggers?.map(trigger => trigger.type))
                        .map(async triggerType => {
                            const triggerDoc = await this.$store.dispatch("plugin/load", {
                                cls: triggerType,
                                commit: false
                            });
                            return Object.keys(triggerDoc?.schema?.outputs?.properties ?? {});
                        })
                );
                return uniqBy(fetchTriggerVarsByType.flat());
            },
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

                    this.editor = monaco.editor.create(this.$el, options);

                    if(!this.input){
                        const name = this.currentTab?.path ?? this.currentTab?.name;
                        const value = this.currentTab?.flow || this.creating ? this.value : this.readFile({namespace: this.$route.params.namespace || this.$route.params.id, path: name})

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
                if(this.view === editorViewTypes.TOPOLOGY) return;

                this.subflowAutocompletionProvider?.dispose();
                this.pebbleAutocompletion?.dispose();
                this.nestedFieldAutocompletionProvider?.dispose();
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
