<template>
    <div class="monaco-editor" />
</template>

<script>
    import {defineComponent} from "vue"

    import "monaco-editor/esm/vs/editor/editor.all.js";
    import "monaco-editor/esm/vs/editor/standalone/browser/iPadShowKeyboard/iPadShowKeyboard.js";
    import "monaco-editor/esm/vs/language/json/monaco.contribution";
    import "monaco-editor/esm/vs/basic-languages/monaco.contribution";
    import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
    import EditorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
    import YamlWorker from "./yaml.worker.js?worker";
    import JsonWorker from "monaco-editor/esm/vs/language/json/json.worker?worker";
    import {configureMonacoYaml} from "monaco-yaml";
    import {yamlSchemas} from "override/utils/yamlSchemas"
    import Utils from "../../utils/utils";
    import YamlUtils from "../../utils/yamlUtils";
    import {uniqBy} from "lodash";
    import {mapState} from "vuex";

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
            ...mapState("core", ["autocompletionSource", "monacoYamlConfigured"])
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
                "default": "vs"
            },
            language: {
                type: String,
                required: true,
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
                "default": false
            }
        },
        emits: ["editorWillMount", "editorDidMount", "change", "update:value"],
        model: {
            event: "change"
        },
        watch: {
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
            language: function (newVal) {
                if (this.editor) {
                    let editor = this.getModifiedEditor();
                    this.monaco.editor.setModelLanguage(editor.getModel(), newVal);
                }
            },
            theme: function (newVal) {
                if (this.editor) {
                    this.monaco.editor.setTheme(newVal);
                }
            }
        },
        mounted: function () {
            let _this = this;

            this.monaco = monaco;
            this.$nextTick(function () {
                _this.initMonaco(monaco);
            });

            if (!this.monacoYamlConfigured) {
                this.$store.commit("core/setMonacoYamlConfigured", true)
                configureMonacoYaml(this.monaco, {
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
                this.subflowAutocompletionProvider = this.monaco.languages.registerCompletionItemProvider("yaml", {
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

                this.nestedFieldAutocompletionProvider = this.monaco.languages.registerCompletionItemProvider(["yaml", "plaintext"], {
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
                            suggestions: await _this.autocompletion(
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
        },
        beforeUnmount: function () {
            this.destroy();
        },
        methods: {
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
                    return range[0] < position.offset && position.offset < range[2];
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
                        kind: this.monaco.languages.CompletionItemKind.Value,
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

                const previousWordCharWithInputsCapture = model.findPreviousMatch("(inputs)?([\\w:])", position, true, false, null, true);
                if (!previousWordCharWithInputsCapture) {
                    return undefined;
                }

                const previousWordOffset = model.getOffsetAt({column: previousWordCharWithInputsCapture.range.startColumn, lineNumber: previousWordCharWithInputsCapture.range.startLineNumber});

                let prefixAtPosition = model.getWordUntilPosition(position);
                if (prefixAtPosition?.word === "") {
                    prefixAtPosition = null;
                }
                const wordAtPosition = model.getWordAtPosition(position);
                const subflowTaskWithRange = subflowsWithRange.reverse().find(subflowWithRange => {
                    const range = subflowWithRange.range;
                    return range[0] < previousWordOffset && previousWordOffset < range[2];
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
                                store: false
                            }
                        )).inputs?.map(input => input.id) ?? [];
                    } catch (e) {
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
                            kind: this.monaco.languages.CompletionItemKind.Value,
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
            async autocompletion(source, lineContent, field, rest, lineNumber, fieldToCompleteIndexes) {
                const flowAsJs = YamlUtils.parse(source);
                let autocompletions;
                switch (field) {
                case "inputs":
                    autocompletions = flowAsJs?.inputs?.map(input => input.id);
                    break;
                case "outputs":
                    autocompletions = flowAsJs?.tasks?.map(task => task.id);
                    break;
                case "labels":
                    autocompletions =  Object.keys(flowAsJs?.labels ?? {});
                    break;
                case "flow":
                    autocompletions = ["id", "namespace", "revision"]
                    break;
                case "execution":
                    autocompletions = ["id", "startDate", "originalId"]
                    break;
                case "vars":
                    autocompletions = Object.keys(flowAsJs?.variables ?? {});
                    break;
                case "trigger":
                    autocompletions = await this.triggerVars(flowAsJs);
                    break;
                default: {
                    let match = field.match(/^outputs\.([^.]+)$/);
                    if (match) {
                        autocompletions = await this.outputsFor(match[1], flowAsJs);
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
            async outputsFor(taskId, flowAsJs) {
                const task = flowAsJs?.tasks?.find(task => task.id === taskId);
                if (!task?.type) {
                    return [];
                }

                const pluginDoc = await this.$store.dispatch("plugin/load", {cls: task.type, commit: false});

                return Object.entries(pluginDoc?.schema?.outputs?.properties ?? {})
                    .map(([propName, propInfo]) => propName + (propInfo.type === "object" ? "." : ""));
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
            initMonaco: function (monaco) {
                let self = this;

                this.$emit("editorWillMount", this.monaco);

                let options = {
                    ...{
                        value: this.value,
                        theme: this.theme,
                        language: this.language,
                        suggest: {
                            showClasses: false,
                        }
                    },
                    ...this.options
                };
                if (this.diffEditor) {
                    this.editor = monaco.editor.createDiffEditor(this.$el, options);
                    let originalModel = monaco.editor.createModel(this.original, this.language);
                    let modifiedModel = monaco.editor.createModel(this.value, this.language);
                    this.editor.setModel({
                        original: originalModel,
                        modified: modifiedModel
                    });
                } else {
                    if (this.schemaType) {
                        options["model"] = monaco.editor.createModel(this.value, this.language, monaco.Uri.parse(`file:///${this.schemaType}-${Utils.uid()}.yaml`))
                    } else {
                        options["model"] = monaco.editor.createModel(this.value.toString(), this.language);
                    }

                    monaco.editor.addKeybindingRule({
                        keybinding: monaco.KeyMod.CtrlCmd | monaco.KeyCode.Space,
                        command: "editor.action.triggerSuggest"
                    })

                    this.editor = monaco.editor.create(this.$el, options);
                }

                let editor = this.getModifiedEditor();
                editor.onDidChangeModelContent(function (event) {
                    let value = editor.getValue();

                    if (self.value !== value) {
                        self.$emit("change", value, event);
                        self.$emit("update:value", value)
                    }
                });
                this.$emit("editorDidMount", this.editor);
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
                this.subflowAutocompletionProvider?.dispose();
                this.nestedFieldAutocompletionProvider?.dispose();
                this.editor?.getModel()?.dispose();
                this.editor?.dispose();

            },
            needReload: function (newValue, oldValue) {
                return oldValue.renderSideBySide !== newValue.renderSideBySide;
            },
            reload: function () {
                this.destroy();
                this.initMonaco(this.monaco);
            },
        },
    });
</script>

<style scoped lang="scss">
    .monaco-editor {
        height: 100%;
        outline: none;
    }

</style>