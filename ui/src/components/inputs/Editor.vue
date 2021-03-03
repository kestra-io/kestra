<template>
    <div class="editor-wrapper">
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
                    language="yaml"
                />
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
            lang: {type: String, required: true},
            navbar: {type: Boolean, default: true},
            fullHeight: {type: Boolean, default: true},
            theme: {type: String, default: "vs-dark"},
        },
        components: {
            MonacoEditor,
            UnfoldLessHorizontal,
            UnfoldMoreHorizontal,
        },
        data() {
            const options = {}

            if (!this.fullHeight) {
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
                    vertical: "hidden",
                };
            }

            return {
                options: {
                    ...{
                        tabSize: 2,
                        fontFamily: "'Source Code Pro', SFMono-Regular, Menlo, Monaco, Consolas, \"Liberation Mono\", \"Courier New\", monospace",
                        fontSize: 12,
                        showFoldingControls: "always",
                        scrollBeyondLastLine: false,
                        roundedSelection: false,
                    },
                    ...options
                },
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
                return this.fullHeight ? this.theme : "vs"
            },
            containerClass() {
                return this.fullHeight ? "" : "single-line"
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
                }

                this.editor.onDidBlurEditorWidget(() => {
                    this.$emit("focusout", editor.getValue());
                })

                this.editor.addCommand(KeyMod.CtrlCmd | KeyCode.KEY_S, () => {
                    this.$emit("onSave", editor.getValue())
                });

                if (!this.fullHeight) {
                    this.editor.addCommand(KeyMod.CtrlCmd | KeyCode.KEY_F, () => {})
                    this.editor.addCommand(KeyMod.CtrlCmd | KeyCode.KEY_H, () => {})
                    this.editor.addCommand(KeyCode.F1, () => {})
                }

                if (this.original === undefined && this.navbar) {
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
                    const containerWidth = container.offsetWidth;

                    if (this.fullHeight) {
                        const fullHeight = window.innerHeight - this.$refs.editorContainer.getBoundingClientRect().y - 55 - 30;
                        container.style.height = `${fullHeight}px`;
                        this.editor.layout({width: containerWidth, height: fullHeight});
                    } else if (containerWidth > 0) {
                        const contentHeight = Math.max(21, this.editor.getContentHeight());
                        container.style.height = `${contentHeight+3}px`;
                        this.editor.layout({width: containerWidth-3,  height: contentHeight});
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
            }
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

    &.single-line {
        padding: $input-padding-y $input-padding-x;
        background: white;
        border: 1px solid $input-border-color;
    }
}

</style>