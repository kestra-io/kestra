<template>
    <div>
        <b-navbar v-if="original === undefined && navbar" type="dark" variant="dark">
            <b-btn-group>
                <b-button @click="autoFold(true)" size="sm" variant="light" v-b-tooltip.hover.top="$t('Fold content lines')">
                    <unfold-less-horizontal />
                </b-button>
                <b-button
                    @click="unfoldAll"
                    size="sm"
                    variant="light"
                    v-b-tooltip.hover.top="$t('Unfold content lines')"
                >
                    <unfold-more-horizontal />
                </b-button>
            </b-btn-group>
        </b-navbar>

        <div class="editor-container" :class="containerClass">
            <div ref="editorContainer" class="editor-wrapper">
                <MonacoEditor
                    ref="monacoEditor"
                    :theme="themeComputed"
                    :value="value"
                    :options="options"
                    :diff-editor="original !== undefined"
                    :original="original"
                    @editorDidMount="editorDidMount"
                    @change="onInput"
                    :language="lang"
                />
                <div
                    v-show="showPlaceholder"
                    class="placeholder"
                    @click="onPlaceholderClick"
                >
                    {{ placeholder }}
                </div>
            </div>
        </div>
    </div>
</template>

<script>
    import UnfoldLessHorizontal from "vue-material-design-icons/UnfoldLessHorizontal";
    import UnfoldMoreHorizontal from "vue-material-design-icons/UnfoldMoreHorizontal";
    const MonacoEditor = () => import("./MonacoEditor");

    export default {
        props: {
            value: {type: String, required: true},
            original: {type: String, default: undefined},
            lang: {type: String, default: undefined},
            navbar: {type: Boolean, default: true},
            input: {type: Boolean, default: false},
            fullHeight: {type: Boolean, default: true},
            theme: {type: String, default: "vs-dark"},
            placeholder: {type: [String, Number], default: ""},
            diffSideBySide: {type: Boolean, default: true},
            readOnly: {type: Boolean, default: false},
        },
        components: {
            MonacoEditor,
            UnfoldLessHorizontal,
            UnfoldMoreHorizontal,
        },
        data() {
            return {
                focus: false,
                editor: undefined
            };
        },
        created() {
            window.addEventListener("resize", this.onResize);
        },
        beforeDestroy() {
            window.removeEventListener("resize", this.onResize);
        },
        computed: {
            themeComputed() {
                return this.theme ? this.theme : (!this.input ? "vs-dark" : "vs")
            },
            containerClass() {
                return [
                    !this.input ? "" : "single-line",
                    "theme-" + this.themeComputed
                ]
            },
            showPlaceholder() {
                return this.input === true && !this.focus && !(this.value !== undefined && this.value !== "");
            },
            options() {
                const options = {}

                if (this.input) {
                    options.lineNumbers = "off"
                    options.folding = false;
                    options.renderLineHighlight = "none"
                    options.wordBasedSuggestions = false;
                    options.occurrencesHighlight= false
                    options.hideCursorInOverviewRuler = true
                    options.overviewRulerBorder = false
                    options.overviewRulerLanes = 0
                    options.lineNumbersMinChars = 0;
                    options.fontSize = 13;
                    options.minimap =  {
                        enabled: false
                    }
                    options.scrollBeyondLastColumn = 0;
                    options.scrollbar = {
                        vertical: "hidden",
                        alwaysConsumeMouseWheel: false,
                        horizontalScrollbarSize: 5
                    };
                    options.find = {
                        addExtraSpaceOnTop: false,
                        autoFindInSelection: "never",
                        seedSearchStringFromSelection: false,
                    }
                    options.contextmenu = false;
                    options.lineDecorationsWidth = 0;
                } else {
                    options.scrollbar = {
                        vertical: this.original !== undefined ? "hidden" : "auto",
                        verticalScrollbarSize: this.original !== undefined ? 0 : 10,
                        alwaysConsumeMouseWheel: false,
                    };
                    options.renderSideBySide = this.diffSideBySide
                }

                if (this.readOnly) {
                    options.readOnly = true
                }

                return  {
                    ...{
                        tabSize: 2,
                        fontFamily: "'Source Code Pro', SFMono-Regular, Menlo, Monaco, Consolas, \"Liberation Mono\", \"Courier New\", monospace",
                        fontSize: 12,
                        showFoldingControls: "always",
                        scrollBeyondLastLine: false,
                        roundedSelection: false,
                    },
                    ...options
                };
            }
        },
        methods: {
            editorDidMount(editor) {
                // avoid double import of monaco editor, use a reference
                const KeyCode = this.$refs.monacoEditor.monaco.KeyCode;
                const KeyMod = this.$refs.monacoEditor.monaco.KeyMod;

                this.editor = editor;

                if (this.original) {
                    this.editor.onDidUpdateDiff(this.onResize);
                } else {
                    this.editor.onDidChangeModelDecorations(this.onResize);

                    this.editor.onDidBlurEditorWidget(() => {
                        this.$emit("focusout", editor.getValue());
                        this.focus = false;
                    })

                    this.editor.onDidFocusEditorText(() => {
                        this.focus = true;
                    })
                }

                this.editor.addCommand(KeyMod.CtrlCmd | KeyCode.KEY_S, () => {
                    this.$emit("onSave", editor.getValue())
                });

                if (this.input) {
                    this.editor.addCommand(KeyMod.CtrlCmd | KeyCode.KEY_F, () => {})
                    this.editor.addCommand(KeyMod.CtrlCmd | KeyCode.KEY_H, () => {})
                    this.editor.addCommand(KeyCode.F1, () => {})
                }

                if (this.original === undefined && this.navbar && this.fullHeight) {
                    this.editor.addAction({
                        id: "fold-multiline",
                        label: "Fold All Multi Lines",
                        keybindings: [
                            KeyCode.F10,
                        ],
                        contextMenuGroupId: "fold",
                        contextMenuOrder: 1.5,
                        run: (ed) => {
                            const foldingContrib = ed.getContribution("editor.contrib.folding");
                            foldingContrib.getFoldingModel().then(foldingModel => {

                                let editorModel = foldingModel.textModel;
                                let regions = foldingModel.regions;
                                let toToggle = [];
                                for (let i = regions.length - 1; i >= 0; i--) {
                                    if (regions.isCollapsed(i) === false) {
                                        let startLineNumber = regions.getStartLineNumber(i);

                                        if (editorModel.getLineContent(startLineNumber).trim().endsWith("|")) {
                                            toToggle.push(regions.toRegion(i));
                                        }
                                    }
                                }
                                foldingModel.toggleCollapseState(toToggle);
                            });

                            return null;
                        }
                    });

                    if (localStorage.getItem("autofoldTextEditor") === "1") {
                        this.autoFold(true);
                    }
                }

                if (this.original !== undefined) {
                    this.editor.updateOptions({readOnly: true})
                }

                this.onResize();
            },
            onResize() {
                if (this.$refs.editorContainer && this.editor) {
                    const container = this.$refs.editorContainer;
                    const containerParent = container.parentElement.parentElement;
                    const containerWidth = containerParent.offsetWidth;

                    if (this.original || this.fullHeight) {
                        const fullHeight = window.innerHeight - this.$refs.editorContainer.getBoundingClientRect().y - 55 - 30;
                        container.style.height = `${fullHeight}px`;
                        this.editor.layout({width: containerWidth, height: fullHeight});
                    } else {
                        const contentHeight = Math.max(21, this.editor.getContentHeight());
                        container.style.height = `${contentHeight+3}px`;
                        container.style.width = `${containerWidth-(this.input ? 20 : 0)}px`;
                        this.editor.layout({width: containerWidth-(this.input ? 20 : 0),  height: contentHeight});
                    }
                }
            },
            autoFold(autoFold) {
                if (autoFold) {
                    this.editor.trigger("fold", "fold-multiline");
                }
            },
            unfoldAll() {
                this.editor.trigger("unfold", "editor.unfoldAll");
            },
            onInput(value) {
                this.$emit("input", value);
            },
            onPlaceholderClick() {
                this.editor.layout()
                this.editor.focus()
            },
        },
    };
</script>
<style scoped lang="scss">
@import "../../styles/variable";

.navbar {
    border: 0;
}
/deep/ .editor-container {
    //.monaco-editor .suggest-widget, .monaco-editor .suggest-details {
    //    border-style: hidden;
    //}

    position: relative;
    max-width: 100%;
    display: flex;

    .placeholder {
        position: absolute;
        top:  $input-padding-y ;
        left: $input-padding-x;
        overflow: hidden;
        padding-left: inherit;
        padding-right: inherit;
        cursor: text;
        user-select: none;
        color: $input-placeholder-color;
    }

    .editor-with-placeholder {
        ::v-deep .view-lines {
            opacity: 0;
        }
    }

    &.single-line {
        padding: $input-padding-y $input-padding-x;
        background: white;
        border: 1px solid $input-border-color;
        min-height: 38px;

        &.theme-vs-dark {
            background: #1e1e1e;
        }
    }
}

</style>