<template>
    <div class="editor-wrapper">
        <b-navbar toggleable="md" type="dark" variant="dark">
            <b-button @click="autoFold" size="sm" variant="light" v-b-tooltip.hover.top="$t('Fold content lines')"><unfold-less-horizontal/></b-button>
            <b-button @click="unfoldAll" size="sm" variant="light" v-b-tooltip.hover.top="$t('Unfold content lines')"><unfold-more-horizontal/></b-button>
        </b-navbar>
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
import UnfoldLessHorizontal from "vue-material-design-icons/UnfoldLessHorizontal";
import UnfoldMoreHorizontal from "vue-material-design-icons/UnfoldMoreHorizontal";

import YamlUtils from '../../utils/yamlUtils';
    export default {
        props: {
            value: { type: String, required: true },
            lang: { type: String, required: true },
            width: { type: String, default: "100%" },
            height: { type: String, default: "100%" },
        },
        components: {
            editor: require("vue2-ace-editor"),
            UnfoldLessHorizontal,
            UnfoldMoreHorizontal
        },
        computed: {
            ed () {
                return this.$refs.aceEditor.editor
            }
        },
        methods: {
            editorInit: function (editor) {
                require("brace/mode/yaml");
                require("brace/theme/merbivore_soft");
                require("brace/ext/language_tools")
                require("brace/ext/error_marker")
                require("brace/ext/searchbox")
                editor.textInput.focus()

                editor.setOptions({
                    minLines: 5,
                    maxLines: Infinity,
                    fontFamily: '"Source Code Pro", SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace',
                    showPrintMargin: false,
                    tabSize: 2,
                    wrap: false,
                    newLineMode: "unix",
                    autoScrollEditorIntoView: true,
                    enableBasicAutocompletion: true,
                    enableLiveAutocompletion: true
                });
                editor.commands.addCommand({
                    name: "save",
                    bindKey: { win: "Ctrl-S", mac: "Cmd-S" },
                    exec: (editor) => {
                        this.$emit('onSave', editor.session.getValue())
                    },
                });
                setTimeout(() => {
                    this.autoFold()
                })
            },
            trimContent(text) {
                return text.split('\n').map(line => line.trim()).join('\n')
            },
            autoFold() {
                //we may add try in case content is not serializable a json
                let trimmedContent = this.trimContent(this.value)
                const foldableTokens = []
                let lineDiff = 0
                this.getFoldLines(YamlUtils.parse(this.value), foldableTokens)
                for (const foldableToken of foldableTokens) {
                    const search = this.trimContent(foldableToken)
                    const index = trimmedContent.indexOf(search)
                    const line = trimmedContent.slice(0, index).split('\n').length + lineDiff
                    lineDiff = line + search.split('\n').length - 2
                    trimmedContent = trimmedContent.slice(index + search.length)
                    this.ed.getSession().$toggleFoldWidget(line - 2, {})
                }
            },
            getFoldLines(node, foldableTokens){
                if (Array.isArray(node)) {
                    for (const n of node) {
                        this.getFoldLines(n, foldableTokens)
                    }
                } else if (typeof(node) === 'object') {
                    for (const key in node) {
                        this.getFoldLines(node[key], foldableTokens)
                    }
                } else if (typeof(node) === 'string') {
                    if (node.split('\n').length > parseInt(localStorage.getItem('autofoldTextEditor') || 3)) {
                        foldableTokens.push(node)
                    }
                }
            },
            unfoldAll() {
                this.ed.getSession().expandFolds(this.ed.getSession().getAllFolds())
            },
            onInput(value) {
                this.$emit('input', value);
            }
        },
    };
</script>
<style scoped>
.editor-wrapper button {
    margin-right: 10px;
}
</style>