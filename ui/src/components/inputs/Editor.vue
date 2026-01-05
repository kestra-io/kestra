<template>
    <div class="ks-editor edit-flow-editor">
        <nav v-if="!isDiff && navbar" class="top-nav">
            <slot name="nav">
                <div class="text-nowrap">
                    <el-button-group>
                        <el-tooltip
                            effect="light"
                            :content="t('Fold content lines')"
                            :persistent="false"
                            transition=""
                            :hideAfter="0"
                        >
                            <el-button
                                :icon="icon.UnfoldLessHorizontal"
                                @click="autoFold(true)"
                                size="small"
                            />
                        </el-tooltip>
                        <el-tooltip
                            effect="light"
                            :content="t('Unfold content lines')"
                            :persistent="false"
                            transition=""
                            :hideAfter="0"
                        >
                            <el-button
                                :icon="icon.UnfoldMoreHorizontal"
                                @click="unfoldAll"
                                size="small"
                            />
                        </el-tooltip>
                    </el-button-group>
                    <slot name="extends-navbar" />
                </div>
            </slot>
        </nav>
        <div class="editor-absolute-container pe-none">
            <slot name="absolute" />
        </div>
        <span v-if="label" class="label">{{ label }}</span>
        <div class="editor-container" ref="container" :class="[containerClass, {'mb-2': label}]">
            <div ref="editorContainer" class="editor-wrapper position-relative">
                <MonacoEditor
                    ref="monacoEditor"
                    :key="isDiff.toString()"
                    :path="path"
                    :theme="themeComputed"
                    :value="modelValue"
                    :options="options"
                    :diffEditor="isDiff"
                    :original="original"
                    :language="lang"
                    :extension="extension"
                    :schemaType="schemaType"
                    :input="input"
                    :creating="creating"
                    :largeSuggestions="largeSuggestions"
                    @mouse-move="emit('mouse-move', $event)"
                    @mouse-leave="emit('mouse-leave', $event)"
                    @change="onInput"
                    @editor-did-mount="editorDidMount"
                />
                <div
                    v-show="showPlaceholder"
                    class="placeholder"
                    @click="onPlaceholderClick"
                >
                    {{ placeholder }}
                </div>
                <div class="position-absolute bottom-right">
                    <slot name="buttons" />
                </div>
            </div>
        </div>
        <Teleport v-if="showWidgetContent" to=".editor-content-widget-content">
            <slot name="widget-content" />
        </Teleport>
    </div>
</template>

<script setup lang="ts">
    /* eslint-disable vue/enforce-style-attribute */
    import {computed, onMounted, ref, shallowRef, watch} from "vue";
    import {useI18n} from "vue-i18n";
    import {useThrottleFn} from "@vueuse/core";
    import UnfoldLessHorizontal from "vue-material-design-icons/UnfoldLessHorizontal.vue";
    import UnfoldMoreHorizontal from "vue-material-design-icons/UnfoldMoreHorizontal.vue";
    import Help from "vue-material-design-icons/Help.vue";
    import {useDocStore} from "../../stores/doc";
    import {useMiscStore} from "override/stores/misc";
    import BookMultipleOutline from "vue-material-design-icons/BookMultipleOutline.vue";
    import Close from "vue-material-design-icons/Close.vue";
    // @ts-expect-error no clean way to have focus on inputs
    import {TabFocus} from "monaco-editor/esm/vs/editor/browser/config/tabFocus";
    import MonacoEditor from "./MonacoEditor.vue";
    import type * as monaco from "monaco-editor/esm/vs/editor/editor.api";
    import {useScrollMemory} from "../../composables/useScrollMemory";

    const {t} = useI18n()


    const props = defineProps({
        modelValue: {type: String, default: ""},
        original: {type: String, default: undefined},
        lang: {type: String, default: undefined},
        path: {type: String, default: undefined},
        extension: {type: String, default: undefined},
        schemaType: {type: String, default: undefined},
        navbar: {type: Boolean, default: true},
        input: {type: Boolean, default: false},
        keepFocused: {type: Boolean, default: undefined},
        largeSuggestions: {type: Boolean, required: false},
        fullHeight: {type: Boolean, default: true},
        customHeight: {type: Number, default: 7},
        theme: {type: String, default: undefined},
        placeholder: {type: [String, Number], default: ""},
        diffSideBySide: {type: Boolean, default: true},
        readOnly: {type: Boolean, default: false},
        wordWrap: {type: Boolean, default: true},
        lineNumbers: {type: Boolean, default: undefined},
        minimap: {type: Boolean, default: false},
        creating: {type: Boolean, default: false},
        label: {type: String, default: undefined},
        shouldFocus: {type: Boolean, default: true},
        showScroll: {type: Boolean, default: false},
        diffOverviewBar: {type: Boolean, default: true},
        scrollKey: {type: String, default: undefined},
    })

    defineOptions({
        name: "Editor",
    })

    const emit = defineEmits<{
        (e: "save", value?: string): void;
        (e: "execute", value?: string): void;
        (e: "focusout", value?: string): void;
        (e: "update:modelValue", value: string): void;
        (e: "cursor", payload: {position: monaco.Position, model: monaco.editor.ITextModel}): void;
        (e: "confirm", value?: string): void;
        (e: "mouse-move", event: monaco.editor.IEditorMouseEvent): void;
        (e: "mouse-leave", event: monaco.editor.IPartialEditorMouseEvent): void;
    }>();


    let editor: monaco.editor.IStandaloneCodeEditor | monaco.editor.IStandaloneDiffEditor | undefined = undefined

    const focus = ref(false)
    const icon = {
        UnfoldLessHorizontal: shallowRef(UnfoldLessHorizontal),
        UnfoldMoreHorizontal: shallowRef(UnfoldMoreHorizontal),
        Help: shallowRef(Help),
        BookMultipleOutline: shallowRef(BookMultipleOutline),
        Close: shallowRef(Close),
    } as const
    const editorDocumentation = ref()
    const preventCursorChange = ref(false)

    onMounted(() => {
        useDocStore().docId = "flowEditor";
    })

    watch(
        () => [props.modelValue, props.lang],
        ([value, newLang], [, oldLang]) => {
            preventCursorChange.value = isCodeEditor(editor) && editor?.getValue?.() !== value;
            if (newLang === oldLang) return;
            if (isDiff.value || !editor || !isCodeEditor(editor)) return;

            const monacoRef = monacoEditor.value?.monaco;
            const model = editor.getModel?.();
            if (!monacoRef || !model) return;

            let lang = "plaintext";
            if (newLang && typeof newLang === "string" && newLang.trim()) {
                lang = newLang.includes("json")
                    ? "json"
                    : newLang.includes("-")
                        ? newLang.split("-")[0]
                        : newLang;
            }
            try {
                monacoRef.editor.setModelLanguage(model, lang);
            } catch (e) {
                console.warn("Failed to set model language", e);
            }
        }
    );


    const themeComputed = computed(() => {
        return useMiscStore().theme;
    })

    const containerClass = computed(() => {
        return [
            !props.input ? "" : "single-line",
            "theme-" + themeComputed.value,
            themeComputed.value === "dark" ? "custom-dark-vs-theme" : "",
        ];
    })

    const showPlaceholder = computed(() => {
        return (
            props.input === true &&
            !props.shouldFocus &&
            (!props.modelValue || (typeof props.modelValue === "string" && props.modelValue.trim() === "")) &&
            !focus.value
        );
    })

    const options = computed(() => {
        const options: monaco.editor.IStandaloneEditorConstructionOptions & {
            renderSideBySide?:boolean
            useInlineViewWhenSpaceIsLimited?:boolean
            renderOverviewRuler?:boolean
        } = {};

        if (props.input && !props.lineNumbers) {
            options.lineNumbers = "off";
            options.folding = false;
            options.renderLineHighlight = "none";
            options.wordBasedSuggestions = "off";
            options.occurrencesHighlight = "off";
            options.hideCursorInOverviewRuler = true;
            options.overviewRulerBorder = false;
            options.overviewRulerLanes = 0;
            options.lineNumbersMinChars = 0;
            options.fontSize = 13;
            options.minimap = {
                enabled: false,
            };
            options.scrollBeyondLastColumn = 0;
            options.overviewRulerLanes = 0;
            options.scrollbar = {
                vertical: !props.showScroll ? "hidden" : "visible",
                horizontal: "hidden",
                alwaysConsumeMouseWheel: false,
                handleMouseWheel: true,
                horizontalScrollbarSize: 0,
                verticalScrollbarSize: !props.showScroll ? 0 : 5,
                useShadows: false,
            };
            options.stickyScroll = {
                enabled: false,
            };
            options.find = {
                addExtraSpaceOnTop: false,
                autoFindInSelection: "never",
                seedSearchStringFromSelection: "never",
            };
            options.contextmenu = false;
            options.lineDecorationsWidth = 0;

        } else {
            options.scrollbar = {
                vertical: isDiff.value ? "hidden" : "auto",
                verticalScrollbarSize: isDiff.value ? 0 : 10,
                alwaysConsumeMouseWheel: false,
            };
            options.renderSideBySide = props.diffSideBySide;
            options.useInlineViewWhenSpaceIsLimited = false;
            options.renderOverviewRuler = props.diffOverviewBar;
        }


        options.minimap = props.minimap ? undefined : {
            enabled: false,
        };

        options.readOnly = props.readOnly;

        options.wordWrap = props.wordWrap ? "on" : "off";
        options.automaticLayout = true;

        const settingsEditorFontSize = localStorage.getItem("editorFontSize")

        return {
            
            tabSize: 2,
            fontFamily: localStorage.getItem("editorFontFamily")
                ? localStorage.getItem("editorFontFamily")
                : "'Source Code Pro', monospace",
            fontSize: settingsEditorFontSize
                ? parseInt(settingsEditorFontSize)
                : 12,
            showFoldingControls: "always",
            scrollBeyondLastLine: false,
            roundedSelection: false,
            ...options,
        } as monaco.editor.IStandaloneEditorConstructionOptions & {
            renderSideBySide?:boolean
            useInlineViewWhenSpaceIsLimited?:boolean
            renderOverviewRuler?:boolean
        };
    })

    editorDocumentation.value =
        localStorage.getItem("editorDocumentation") !== "false" &&
        props.navbar;

    const monacoEditor = ref<InstanceType<typeof MonacoEditor>>()
    const container = ref<HTMLDivElement>();

    let lastTimeout: number | undefined = undefined
    let decorations: monaco.editor.IEditorDecorationsCollection | undefined = undefined

    const isDiff = computed(() => props.original !== undefined);

    function isCodeEditor(editor?: monaco.editor.IStandaloneCodeEditor | monaco.editor.IStandaloneDiffEditor): editor is monaco.editor.IStandaloneCodeEditor{
        return editor?.getEditorType() === monacoEditor.value?.monaco.editor.EditorType.ICodeEditor
    }

    const scrollMemory = props.scrollKey ? useScrollMemory(ref(props.scrollKey)) : null;

    function editorDidMount(monacoMounted?: monaco.editor.IStandaloneCodeEditor | monaco.editor.IStandaloneDiffEditor) {

        const monacoRef = monacoEditor.value

        editor = monacoMounted;

        if(!editor || !monacoRef) {
            console.error("Monaco editor is not mounted properly.");
            return;
        }

        // avoid double import of monaco editor, use a reference
        const KeyCode = monacoRef.monaco.KeyCode;
        const KeyMod = monacoRef.monaco.KeyMod;

        decorations = editor.createDecorationsCollection();

        if(!isCodeEditor(editor)){
            return
        }

        const codeEditor = editor as monaco.editor.IStandaloneCodeEditor;
        
        
        if (props.scrollKey && scrollMemory) {
            const savedState = scrollMemory.loadData<monaco.editor.ICodeEditorViewState>("viewState");
            if (savedState) {
                codeEditor.restoreViewState(savedState);
                codeEditor.revealLineInCenterIfOutsideViewport?.(codeEditor.getPosition()?.lineNumber ?? 1);
            }
            
            const top = scrollMemory.loadData<number>("scrollTop", 0);
            if (typeof top === "number") {
                codeEditor.setScrollTop(top);
            }
            
            const throttledSave = useThrottleFn(() => {
                scrollMemory.saveData(codeEditor.saveViewState(), "viewState");
                scrollMemory.saveData(codeEditor.getScrollTop(), "scrollTop");
            }, 100);
            
            codeEditor.onDidScrollChange?.(throttledSave);
        }

        if (!isDiff.value) {
            editor.onDidBlurEditorWidget?.(() => {
                emit("focusout", isCodeEditor(editor)
                    ? editor.getValue()
                    : undefined);
                focus.value = false;
            });

            if(props.shouldFocus){
                editor.onDidFocusEditorText?.(() => {
                    focus.value = true;
                });

                monacoRef?.focus();
            }
        }

        if (!props.readOnly) {
            editor.addAction({
                id: "kestra-save",
                label: t("save"),
                keybindings: [KeyMod.CtrlCmd | KeyCode.KeyS],
                contextMenuGroupId: "navigation",
                contextMenuOrder: 1.5,
                run: (ed) => {
                    emit("save", ed.getValue());
                },
            });
        } else {
            if (props.lang === "json") {
                editor.getAction("editor.action.formatDocument")?.run();
            }
        }

        editor.addAction({
            id: "kestra-execute",
            label: t("execute flow behaviour"),
            keybindings: [KeyMod.CtrlCmd | KeyCode.KeyE],
            contextMenuGroupId: "navigation",
            contextMenuOrder: 1.5,
            run: (ed) => {
                emit("execute", ed.getValue());
            },
        });

        editor.addAction({
            id: "confirm",
            label: t("confirm"),
            keybindings: [KeyMod.CtrlCmd | KeyCode.Enter],
            contextMenuGroupId: "navigation",
            contextMenuOrder: 1.5,
            run: (ed) => {
                emit("confirm", ed.getValue());
            },
        });

        // TabFocus is global to all editor so revert the behavior on non inputs
        editor.onDidFocusEditorText?.(() => {
            TabFocus.setTabFocusMode(props.keepFocused === undefined ? props.input : false);
        });

        if (props.input) {
            editor.addAction({
                id: "prevent-ctrl-h",
                label: "Prevent CTRL + H",
                keybindings: [KeyMod.CtrlCmd | KeyCode.KeyH],
                run: () => {}
            });

            editor.addAction({
                id: "prevent-f1",
                label: "Prevent F1",
                keybindings: [KeyCode.F1],
                run: () => {}
            });

            if (!props.readOnly) {
                editor.addAction({
                    id: "prevent-ctrl-f",
                    label: "Prevent CTRL + F",
                    keybindings: [KeyMod.CtrlCmd | KeyCode.KeyF],
                    run: () => {}
                });
            }
        }

        if (!isDiff.value && props.navbar && props.fullHeight) {
            editor.addAction({
                id: "fold-multiline",
                label: t("fold_all_multi_lines"),
                keybindings: [KeyCode.F10],
                contextMenuGroupId: "fold",
                contextMenuOrder: 1.5,
                async run(ed) {
                    const foldingContrib = ed.getContribution(
                        "editor.contrib.folding",
                    ) as any;
                    const foldingModel = await foldingContrib?.getFoldingModel();
                    let editorModel = foldingModel.textModel;
                    let regions = foldingModel.regions;
                    let toToggle = [];
                    for (let i = regions.length - 1; i >= 0; i--) {
                        if (regions.isCollapsed(i) === false) {
                            let startLineNumber =
                                regions.getStartLineNumber(i);

                            if (
                                editorModel
                                    .getLineContent(startLineNumber)
                                    .trim()
                                    .endsWith("|")
                            ) {
                                toToggle.push(regions.toRegion(i));
                            }
                        }
                    }
                    foldingModel.toggleCollapseState(toToggle);

                    return;
                },
            });

            if (localStorage.getItem("autofoldTextEditor") === "true") {
                autoFold(true);
            }
        }

        if (!props.fullHeight) {
            editor.onDidContentSizeChange((e) => {
                if (!container.value) return;
                container.value.style.height =
                    e.contentHeight + props.customHeight + "px";
            });
        }

        if (!isDiff.value) {
            editor.onDidContentSizeChange((_) => {
                highlightPebble();
            });

            editor.onDidChangeCursorPosition?.(() => {
                clearTimeout(lastTimeout);
                if(!editor) return
                if(preventCursorChange.value) {
                    preventCursorChange.value = false;
                    return;
                }
                if(!isCodeEditor(editor))return
                let position = editor.getPosition();
                let model = editor.getModel();
                lastTimeout = setTimeout(() => {
                    if(!position || !model) return;
                    emit("cursor", {
                        position: position,
                        model: model,
                    });
                    // Save view state when cursor changes
                    if (scrollMemory) {
                        scrollMemory.saveData(codeEditor.saveViewState(), "viewState");
                    }
                }, 100) as unknown as number;
                highlightPebble();
            });
        }

        // attach an imperative method to the element so tests can programmatically update
        // the value of the editor without dealing with how Monaco handles the exact keystrokes
        monacoRef.$el.querySelector(".ks-monaco-editor").__setValueInTests = (value: string) => {
            if(!isCodeEditor(editor))return
            editor?.setValue(value);
        };
    }

    function autoFold(autoFold?: boolean) {
        if (autoFold && editor) {
            editor.trigger("fold", "fold-multiline", {});
        }
    }

    function unfoldAll() {
        editor?.trigger("unfold", "editor.unfoldAll", {});
    }

    function onInput(value: string) {
        emit("update:modelValue", value);
    }

    function onPlaceholderClick() {
        editor?.layout();
        editor?.focus();
    }

    const decorationsLists: {
        pebble?: monaco.editor.IModelDeltaDecoration[],
        lines?: monaco.editor.IModelDeltaDecoration[]
    } = {}

    function getHighlightDecoration(range: {start: number, end: number}) {
        if (!monacoEditor.value) return ;
        const monacoRef = monacoEditor.value.monaco;
        return [{
            range: new monacoRef.Range(range.start, 1, range.end, 1),
            options: {
                isWholeLine: true,
                className: "highlight-lines",
            },
        }] as monaco.editor.IModelDeltaDecoration[];
    }

    function highlightLinesRange(range: {start: number, end: number}) {
        decorationsLists.lines = getHighlightDecoration(range);
        setDecorations();
    }


    function clearLinesRangeHighlights() {
        decorationsLists.lines = [];
        setDecorations();
    }

    defineExpose({
        highlightLinesRange,
        clearLinesRangeHighlights,
        addContentWidget,
        removeContentWidget,
    })

    function setDecorations() {
        decorations?.clear()
        if(decorationsLists.lines){
            decorations?.append(decorationsLists.lines);
        }
        if(decorationsLists.pebble){
            decorations?.append(decorationsLists.pebble);
        }
    }

    function highlightPebble() {
        if(!isCodeEditor(editor))return
        // Highlight code that match pebble content
        let model = editor?.getModel?.();
        let text = model?.getValue?.();
        let regex = new RegExp("\\{\\{(.+?)}}", "g");
        let match;
        const decorationsToAdd: monaco.editor.IModelDeltaDecoration[] = [];
        while (text && model && (match = regex.exec(text)) !== null) {
            let startPos = model.getPositionAt(match.index);
            let endPos = model.getPositionAt(match.index + match[0].length);
            decorationsToAdd.push({
                range: {
                    startLineNumber: startPos.lineNumber,
                    startColumn: startPos.column,
                    endLineNumber: endPos.lineNumber,
                    endColumn: endPos.column,
                },
                options: {
                    inlineClassName: "highlight-pebble",
                },
            });
        }

        decorationsLists.pebble = decorationsToAdd;
        setDecorations();
    }

    const widgetNode = (() => {
        const node = document.createElement("div");
        node.className = "editor-content-widget";
        const content = document.createElement("div")
        content.className = "editor-content-widget-content";
        node.appendChild(content)
        return node;
    })()

    const showWidgetContent = ref(false)

    async function addContentWidget(widget: {
        id: string;
        position: monaco.IPosition;
        height: number
        right: string
    }) {
        if(!isCodeEditor(editor)) return
        if(!monacoEditor.value) return
        const monacoRefTypes = monacoEditor.value.monaco
        editor?.addContentWidget({
            getId(){
                return widget.id
            },
            getPosition(){
                return {
                    position: widget.position,
                    preference: [
                        monacoRefTypes.editor.ContentWidgetPositionPreference.EXACT,
                    ],
                }
            },
            getDomNode: () => {
                const content = widgetNode.querySelector(".editor-content-widget-content") as HTMLDivElement;
                if(content){
                    content.style.height = widget.height + "rem";
                }
                return widgetNode;
            },
            afterRender() {
                const boundingClientRect = monacoEditor.value!.$el.querySelector(".ks-monaco-editor .monaco-scrollable-element").getBoundingClientRect();
                // Since we must position the widget on the right side but our anchor is from the left, we add the width of the editor minus the right offset (150px is a rough estimate of the widget's width)
                widgetNode.style.left = `calc(${boundingClientRect.width}px - 150px - ${widget.right})`;
            }
        });

        await waitForWidgetContentNode()

        showWidgetContent.value = true
    }

    async function wait(time: number){
        return new Promise(resolve => setTimeout(resolve, time));
    }

    async function waitForWidgetContentNode() {
        await wait(30);
        if (document.querySelector(".editor-content-widget-content") === null) {
            return waitForWidgetContentNode();
        }
    }

    function removeContentWidget(id: string) {
        showWidgetContent.value = false;
        if(!isCodeEditor(editor)) return
        editor?.removeContentWidget({
            getId: () => id,
            getPosition(){
                return {
                    position: {lineNumber: 0, column: 0},
                    preference: [],
                }
            },
            getDomNode: () => {
                return widgetNode;
            },
        });
    }
</script>

<style lang="scss">
@import "@kestra-io/ui-libs/src/scss/color-palette.scss";
@import "../../styles/layout/root-dark.scss";

.highlight-lines{
    background-color: rgba($base-blue-400, .2);
}

.editor-content-widget-content{
    display: flex;
    align-items: center;
    justify-content: center;

    .el-button-group {
        display: inline-flex;
    }
}

:not(.namespace-defaults, .el-drawer__body) > .ks-editor {
    flex-direction: column;
    height: 100%;
    z-index: 1001;
}

.el-form .ks-editor {
    display: flex;
    width: 100%;
}

.ks-editor {
    display: flex;
    overflow: hidden;
    .top-nav {
        background-color: var(--ks-background-card);
        padding: 0.5rem;
        border-radius: var(--bs-border-radius-lg);
        border-bottom-left-radius: 0;
        border-bottom-right-radius: 0;

        html.dark & {
            background-color: var(--bs-gray-100);
        }
    }

    .editor-absolute-container {
        position: absolute;
        top: 8px;
        right: 20px;
        z-index: 10;
        color: var(--ks-content-secondary);
        cursor: pointer;
    }

    .editor-absolute-container > * {
        pointer-events: auto;
    }

    .editor-container {
        display: flex;
        flex-grow: 1;

        &.single-line {
            min-height: var(--el-component-size);
            padding: 1px 11px;
            background-color: var(
                --el-input-bg-color,
                var(--el-fill-color-blank)
            );
            border-radius: var(
                --el-input-border-radius,
                var(--el-border-radius-base)
            );
            transition: var(--el-transition-box-shadow);
            box-shadow: 0 0 0 1px var(--ks-border-primary) inset;
            padding-top: 7px;

            &.custom-dark-vs-theme {
                background-color: var(--ks-background-input);
            }

            &.theme-light {
                background-color: $base-white;
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
            color: var(--ks-content-inactive);
        }

        .editor-wrapper {
            min-width: 75%;
            width: 100%;

            .monaco-hover-content {
                h4 {
                    font-size: var(--font-size-base);
                    font-weight: bold;
                    line-height: var(--bs-body-line-height);
                }

                p {
                    margin-bottom: 0.5rem;

                    &:last-child {
                        display: none;
                    }
                }

                *:nth-last-child(2n) {
                    margin-bottom: 0;
                }
            }
        }

        .bottom-right {
            bottom: 0px;
            right: 0px;

            ul {
                display: flex;
                list-style: none;
                padding: 0;
                margin: 0;
                //gap: .5rem;
            }
        }
    }
}

.custom-dark-vs-theme {
    .monaco-editor,
    .monaco-editor-background {
        outline: none;
        background-color: var(--ks-background-input);
        --vscode-editor-background: var(--ks-background-input);
        --vscode-breadcrumb-background: var(--ks-background-input);
        --vscode-editorGutter-background: var(--ks-background-input);
    }

    .monaco-editor .margin {
        background-color: var(--ks-background-input);
        --vscode-editorGutter-background: var(--ks-background-input);
        --vscode-editorLineNumber-activeForeground: var(--ks-content-secondary);
        --vscode-editorLineNumber-foreground: var(--ks-content-secondary);
        --vscode-editorLineNumber-rangeHighlightBackground: var(--ks-content-secondary);
    }
}

.highlight-text {
    cursor: pointer;
    font-weight: 700;
    box-shadow: 0 19px 44px rgba(157, 29, 236, 0.31);

    html.dark & {
        background-color: rgba(255, 255, 255, 0.2);
    }
}

.highlight-pebble {
    color: #977100 !important;

    html.dark & {
        color: #ffca16 !important;
    }
}

.disable-text {
    color: var(--ks-content-inactive) !important;
}

div.img {
    min-height: 130px;
    height: 100%;

    &.get-started {
        background: url("../../assets/onboarding/onboarding-doc-light.svg")
            no-repeat center;

        html.dark & {
            background: url("../../assets/onboarding/onboarding-doc-dark.svg")
                no-repeat center;
        }
    }
}
</style>
