<template>
    <div class="ks-editor">
        <nav v-if="original === undefined && navbar" class="top-nav">
            <el-button-group>
                <el-tooltip :content="$t('Fold content lines')" :persistent="false" transition="" :hide-after="0">
                    <el-button :icon="icon.UnfoldLessHorizontal" @click="autoFold(true)" size="small" />
                </el-tooltip>
                <el-tooltip :content="$t('Unfold content lines')" :persistent="false" transition="" :hide-after="0">
                    <el-button :icon="icon.UnfoldMoreHorizontal" @click="unfoldAll" size="small" />
                </el-tooltip>
                <el-tooltip v-if="schemaType === 'flow'" :content="$t('Reset guided tour')" :persistent="false" transition="" :hide-after="0">
                    <el-button :icon="icon.Help" @click="restartGuidedTour" size="small" />
                </el-tooltip>
            </el-button-group>
        </nav>

        <div class="editor-container" ref="container" :class="containerClass">
            <div ref="editorContainer" class="editor-wrapper">
                <monaco-editor
                    ref="monacoEditor"
                    :theme="themeComputed"
                    :value="modelValue"
                    :options="options"
                    :diff-editor="original !== undefined"
                    :original="original"
                    @change="onInput"
                    @editor-did-mount="editorDidMount"
                    :language="lang"
                    :schema-type="schemaType"
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
    import {defineAsyncComponent, shallowRef} from "vue"
    import UnfoldLessHorizontal from "vue-material-design-icons/UnfoldLessHorizontal.vue";
    import UnfoldMoreHorizontal from "vue-material-design-icons/UnfoldMoreHorizontal.vue";
    import Help from "vue-material-design-icons/Help.vue";
    import {mapGetters} from "vuex";

    const MonacoEditor = defineAsyncComponent(() =>
        import("./MonacoEditor.vue")
    )

    export default {
        props: {
            modelValue: {type: String, default: ""},
            original: {type: String, default: undefined},
            lang: {type: String, default: undefined},
            schemaType: {type: String, default: undefined},
            navbar: {type: Boolean, default: true},
            input: {type: Boolean, default: false},
            fullHeight: {type: Boolean, default: true},
            theme: {type: String, default: undefined},
            placeholder: {type: [String, Number], default: ""},
            diffSideBySide: {type: Boolean, default: true},
            readOnly: {type: Boolean, default: false},
            lineNumbers: {type: Boolean, default: undefined},
            minimap: {type: Boolean, default: true},
        },
        components: {
            MonacoEditor,
        },
        emits: ["save", "focusout", "tab", "update:modelValue"],
        editor: undefined,
        data() {
            return {
                focus: false,
                icon: {
                    UnfoldLessHorizontal: shallowRef(UnfoldLessHorizontal),
                    UnfoldMoreHorizontal: shallowRef(UnfoldMoreHorizontal),
                    Help: shallowRef(Help),
                },
                oldDecorations: [],
            };
        },
        computed: {
            themeComputed() {
                const darkTheme = document.getElementsByTagName("html")[0].className.indexOf("dark") >= 0;

                return this.theme ? this.theme : (localStorage.getItem("editorTheme") || (darkTheme ? "dark" : "vs"))
            },
            containerClass() {
                return [
                    !this.input ? "" : "single-line",
                    !this.fullHeight ? "" : "full-height",
                    !this.original ? "" : "diff",
                    "theme-" + this.themeComputed
                ]
            },
            showPlaceholder() {
                return this.input === true && !this.focus &&
                    (!Object.hasOwn(this, "editor") || this.editor === undefined|| !(this.editor.getValue() !== undefined && this.editor.getValue() !== ""));
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
                    options.overviewRulerLanes = 0;
                    options.scrollbar = {
                        vertical: "hidden",
                        horizontal: "hidden",
                        alwaysConsumeMouseWheel: false,
                        handleMouseWheel: false,
                        horizontalScrollbarSize: 0,
                        verticalScrollbarSize: 0,
                        useShadows: false,
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

                if (this.minimap === false) {
                    options.minimap =  {
                        enabled: false

                    }
                }

                if (this.readOnly) {
                    options.readOnly = true
                }

                options.wordWrap = true
                options.automaticLayout = true;

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
            },
            ...mapGetters("core", ["guidedProperties"]),
        },
        methods: {
            editorDidMount(editor) {
                // avoid double import of monaco editor, use a reference
                const KeyCode = this.$refs.monacoEditor.monaco.KeyCode;
                const KeyMod = this.$refs.monacoEditor.monaco.KeyMod;

                this.editor = editor;

                if (!this.original) {
                    this.editor.onDidBlurEditorWidget(() => {
                        this.$emit("focusout", editor.getValue());
                        this.focus = false;
                    })

                    this.editor.onDidFocusEditorText(() => {
                        this.focus = true;
                    })
                }

                this.editor.addAction({
                    id: "kestra-save",
                    label: "Save",
                    keybindings: [
                        KeyMod.CtrlCmd | KeyCode.KeyS,
                    ],
                    contextMenuGroupId: "navigation",
                    contextMenuOrder: 1.5,
                    run: (ed) => {
                        this.$emit("save", ed.getValue())
                    }
                });

                if (this.input) {
                    this.editor.addCommand(KeyMod.CtrlCmd | KeyCode.KeyF, () => {})
                    this.editor.addCommand(KeyMod.CtrlCmd | KeyCode.KeyH, () => {})
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

                if (!this.fullHeight) {
                    editor.onDidContentSizeChange(e => {
                        this.$refs.container.style.height = (e.contentHeight + 7) + "px";
                    });
                }

                this.editor.onDidContentSizeChange(_ => {
                    if (this.guidedProperties.monacoRange) {
                        editor.revealLine(this.guidedProperties.monacoRange.endLineNumber);
                        let decorations = [
                            {
                                range: this.guidedProperties.monacoRange,
                                options: {
                                    isWholeLine: true,
                                    inlineClassName: "highlight-text"
                                },
                                className: "highlight-text",
                            }
                        ];
                        decorations = this.guidedProperties.monacoDisableRange ? decorations.concat([
                            {
                                range: this.guidedProperties.monacoDisableRange,
                                options: {
                                    isWholeLine: true,
                                    inlineClassName: "disable-text"
                                },
                                className: "disable-text",
                            },
                        ]) : decorations;
                        this.oldDecorations = this.editor.deltaDecorations(this.oldDecorations, decorations)
                    } else {
                        this.oldDecorations = this.editor.deltaDecorations(this.oldDecorations, []);
                    }
                });
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
                this.$emit("update:modelValue", value);
            },
            onPlaceholderClick() {
                this.editor.layout()
                this.editor.focus()
            },
            restartGuidedTour() {
                localStorage.setItem("tourDoneOrSkip", undefined);
                this.$store.commit("core/setGuidedProperties", {
                    tourStarted:false,
                    flowSource: undefined,
                    saveFlow: false,
                    executeFlow: false,
                    validateInputs: false,
                    monacoRange: undefined,
                    monacoDisableRange: undefined}
                );
                this.$tours["guidedTour"].start();
            },
        },
    };
</script>

<style lang="scss">
    .ks-editor {
        width: 100%;
        .top-nav {
            background-color: var(--bs-gray-300);
            padding: calc(var(--spacer) / 2);
            border-radius: var(--bs-border-radius-lg);
            border-bottom-left-radius: 0;
            border-bottom-right-radius: 0;

            html.dark & {
                background-color: var(--bs-gray-100);
            }
        }

        .editor-container {
            display: flex;
            &.full-height {
                height: calc(100vh - 361px);
            }

            &.diff {
                height: calc(100vh - 435px);
            }

            &.single-line {
                min-height: var(--el-component-size);
                padding: 1px 11px;
                background-color: var(--el-input-bg-color, var(--el-fill-color-blank));
                border-radius: var(--el-input-border-radius, var(--el-border-radius-base));
                transition: var(--el-transition-box-shadow);
                box-shadow: 0 0 0 1px var(--bs-border-color) inset;
                padding-top: 7px;

                html.dark & {
                    background-color: var(--bs-gray-100-darken-5);
                }
            }

            .placeholder {
                position: absolute;
                top: -3px;
                overflow: hidden;
                padding-left: inherit;
                padding-right: inherit;
                cursor: text;
                user-select: none;
                color: var(--el-text-color-placeholder);
            }

            .editor-wrapper {
                width: 100%;
                position: relative;

                .monaco-hover-content {
                    h4 {
                        font-size: var(--font-size-base);
                        font-weight: bold;
                        line-height: var(--bs-body-line-height);
                    }

                    p {
                        margin-bottom: calc(var(--spacer) / 2);
                        &:last-child {
                            display: none;
                        }
                    }

                    *:nth-last-child(2n) {
                        margin-bottom: 0;
                    }
                }
            }

        }
    }

    html.dark {
        .monaco-editor, .monaco-editor-background {
            background-color: var(--bs-gray-100-darken-5);
            --vscode-editor-background: var(--bs-gray-100-darken-5);
            --vscode-breadcrumb-background: var(--bs-gray-100-darken-5);
            --vscode-editorGutter-background: var(--bs-gray-100-darken-5);
        }

        .monaco-editor .margin {
            background-color: var(--bs-gray-100-darken-5);
        }
    }

    .highlight-text {
        cursor: pointer;
        font-weight: 700;
        box-shadow: 0 19px 44px rgba(157, 29, 236, 0.31);

        html.dark & {
            background-color: rgba(255,255,255,0.2);
        }
    }

    .disable-text {
        color: grey !important;
    }

</style>