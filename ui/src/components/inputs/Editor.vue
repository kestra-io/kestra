<template>
    <div class="editor-wrapper">
        <editor
            ref="aceEditor"
            :value="value"
            @input="onInput"
            @init="editorInit"
            :lang="lang"
            theme="merbivore_soft"
            :width="width"
            minLines="5"
            :height="height"
        ></editor>
    </div>
</template>

<script>
    export default {
        props: {
            value: { type: String, required: true },
            lang: { type: String, required: true },
            width: { type: String, default: "100%" },
            height: { type: String, default: "100%" },
        },

        components: {
            editor: require("vue2-ace-editor"),
        },
        methods: {
            editorInit: function (editor) {
                require("brace/mode/yaml");
                require("brace/theme/merbivore_soft");
                require("brace/ext/language_tools")
                require("brace/ext/error_marker")
                require("brace/ext/searchbox")
                this.$refs.aceEditor.editor.textInput.focus()

                editor.setOptions({
                    minLines: 5,
                    maxLines: Infinity,
                    fontFamily: '"Source Code Pro", SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace',
                    showPrintMargin: false,
                    tabSize: 2,
                    wrap: false,
                    newLineMode: "unix",
                });
            },

            onInput(value) {
                this.$emit('input', value);
            }
        }
    };
</script>
