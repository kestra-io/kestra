export default {
    name: "MonacoEditor",
    props: {
        original: String,
        value: {
            type: String,
            required: true
        },
        theme: {
            type: String,
            "default": "vs"
        },
        language: String,
        options: Object,
        schemas: Array,
        diffEditor: {
            type: Boolean,
            "default": false
        }
    },
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

        let monaco = require("monaco-editor/esm/vs/editor/editor.api.js");

        this.monaco = monaco;
        this.$nextTick(function () {
            _this.initMonaco(monaco);
        });
    },
    beforeDestroy: function() {
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
                    language: this.language
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
                if (this.schemas !== undefined) {
                    monaco.languages.yaml.yamlDefaults.setDiagnosticsOptions({
                        enableSchemaRequest: true,
                        hover: true,
                        completion: true,
                        validate: true,
                        format: true,
                        schemas: this.schemas.map(r => {
                            return {
                                uri: r,
                                fileMatch: ["*"]
                            }
                        })
                    });
                }

                this.editor = monaco.editor.create(this.$el, options);
            }

            let editor = this.getModifiedEditor();
            editor.onDidChangeModelContent(function (event) {
                let value = editor.getValue();

                if (self.value !== value) {
                    self.$emit("change", value, event);
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
    render: function render(h) {
        return h("div");
    }
};


