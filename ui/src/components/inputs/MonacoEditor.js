module.exports = {
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
            handler: function handler(options) {
                if (this.editor) {
                    let editor = this.getModifiedEditor();
                    editor.updateOptions(options);
                }
            }
        },
        value: function value(newValue) {
            if (this.editor) {
                let editor = this.getModifiedEditor();

                if (newValue !== editor.getValue()) {
                    editor.setValue(newValue);
                }
            }
        },
        original: function original(newValue) {
            if (this.editor && this.diffEditor) {
                let editor = this.getOriginalEditor();

                if (newValue !== editor.getValue()) {
                    editor.setValue(newValue);
                }
            }
        },
        language: function language(newVal) {
            if (this.editor) {
                let editor = this.getModifiedEditor();
                this.monaco.editor.setModelLanguage(editor.getModel(), newVal);
            }
        },
        theme: function theme(newVal) {
            if (this.editor) {
                this.monaco.editor.setTheme(newVal);
            }
        }
    },
    mounted: function mounted() {
        let _this = this;

        let monaco = require("monaco-editor/esm/vs/editor/editor.api.js");

        this.monaco = monaco;
        this.$nextTick(function () {
            _this.initMonaco(monaco);
        });
    },
    beforeDestroy: function beforeDestroy() {
        this.editor && this.editor.dispose();
    },
    methods: {
        initMonaco: function initMonaco(monaco) {
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
        getEditor: function getEditor() {
            return this.editor;
        },
        getModifiedEditor: function getModifiedEditor() {
            return this.diffEditor ? this.editor.getModifiedEditor() : this.editor;
        },
        getOriginalEditor: function getOriginalEditor() {
            return this.diffEditor ? this.editor.getOriginalEditor() : this.editor;
        },
        focus: function focus() {
            this.editor.focus();
        }
    },
    render: function render(h) {
        return h("div");
    }
};


