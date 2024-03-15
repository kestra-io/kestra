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
                handler: function(newValue, oldValue) {
                    if (this.editor && this.needReload(newValue, oldValue)) {
                        this.reload();
                    } else {
                        this.editor.updateOptions(newValue);
                    }
                }
            },
            value: function(newValue) {
                if (this.editor) {
                    let editor = this.getModifiedEditor();

                    if (newValue !== editor.getValue()) {
                        editor.setValue(newValue);
                    }
                }
            },
            original: function(newValue) {
                if (this.editor && this.diffEditor) {
                    let editor = this.getOriginalEditor();

                    if (newValue !== editor.getValue()) {
                        editor.setValue(newValue);
                    }
                }
            },
            language: function(newVal) {
                if (this.editor) {
                    let editor = this.getModifiedEditor();
                    this.monaco.editor.setModelLanguage(editor.getModel(), newVal);
                }
            },
            theme: function(newVal) {
                if (this.editor) {
                    this.monaco.editor.setTheme(newVal);
                }
            }
        },
        mounted: function() {
            let _this = this;

            this.monaco = monaco;
            this.$nextTick(function () {
                _this.initMonaco(monaco);
            });

            configureMonacoYaml(this.monaco, {
                enableSchemaRequest: true,
                hover: true,
                completion: true,
                validate: true,
                format: true,
                schemas: yamlSchemas(this.$store)
            });
        },
        beforeUnmount: function() {
            this.destroy();
        },
        methods: {
            initMonaco: function(monaco) {
                let self = this;

                this.$emit("editorWillMount", this.monaco);

                let options = {
                    ...{
                        value: this.value,
                        theme: this.theme,
                        language: this.language,
                        suggest: {
                            showClasses: false
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
                        keybinding:  monaco.KeyMod.CtrlCmd | monaco.KeyCode.Space,
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
            getEditor: function() {
                return this.editor;
            },
            getModifiedEditor: function() {
                return this.diffEditor ? this.editor.getModifiedEditor() : this.editor;
            },
            getOriginalEditor: function() {
                return this.diffEditor ? this.editor.getOriginalEditor() : this.editor;
            },
            focus: function() {
                this.editor.focus();
            },
            destroy: function() {
                if (this.editor) {
                    if(this.diffEditor) {
                        this.editor.getModel().original.dispose();
                        this.editor.getModel().modified.dispose();
                    } else {
                        this.editor.getModel().dispose()
                    }
                    this.editor.dispose();
                }
            },
            needReload: function(newValue, oldValue) {
                return oldValue.renderSideBySide !== newValue.renderSideBySide;
            },
            reload: function() {
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