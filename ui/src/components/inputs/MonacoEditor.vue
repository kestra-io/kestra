<template>
    <div>
        <div data-testid="monaco-editor" class="ks-monaco-editor" ref="editorRef" />
        <div ref="datePickerWrapper" v-show="datePickerShown">
            <ElDatePicker
                ref="datePicker"
                type="datetime"
                v-model="selectedDate"
                :teleported="false"
                :defaultValue="nowMoment.toDate()"
                @change="datePickerCallback"
                @keydown.esc.prevent="editorResolved?.focus()"
                @keydown.enter.prevent="datePickerCallback"
                :clearable="false"
                class="z-3"
            />
        </div>

        <textarea
            data-testid="monaco-editor-hidden-synced-textarea"
            style="height: 0; width: 0; opacity: 0;"
            type="text"
            v-model="textAreaValue"
        />
    </div>
</template>

<script lang="ts">
    import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
    import EditorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
    import JsonWorker from "monaco-editor/esm/vs/language/json/json.worker?worker";
    import TypeScriptWorker from "monaco-editor/esm/vs/language/typescript/ts.worker?worker";
    import YamlWorker from "./yaml.worker.js?worker";

    const NodeTypesRaw = import.meta.glob("/node_modules/@types/node/**/*.d.ts", {eager: true, query: "?raw", import: "default"});

    let tries = 0
    function loadNodeTypes(){
        if(monaco.languages.typescript) {
            for(const path in NodeTypesRaw){
                const NodeTypesRawContent = NodeTypesRaw[path] as string;
                // We add every .d.ts file to Monaco
                monaco.languages.typescript.typescriptDefaults.addExtraLib(
                    NodeTypesRawContent,
                    `file://${path}`
                );
            }
        } else if(tries <= 15) {
            // Retry loading types up to 15 times with increasing delay
            setTimeout(loadNodeTypes, ++tries * 100)
        }
    }

    loadNodeTypes();


    export type ThemeBase = editor.BuiltinTheme | "light" | "dark";

    export type EditorOptions = monaco.editor.IStandaloneEditorConstructionOptions & { renderSideBySide?: boolean };

    window.MonacoEnvironment = {
        getWorker(_moduleId, label) {
            switch (label) {
            case "editorWorkerService":
                return new EditorWorker();
            case "yaml":
                return new YamlWorker();
            case "json":
                return new JsonWorker();
            case "javascript":
            case "typescript":
                return new TypeScriptWorker();
            default:
                throw new Error(`Unknown label ${label}`);
            }
        },
    };

    function isCursorInPebbleBlock(editor: monaco.editor.ICodeEditor) {
        const editorValue = editor.getValue()
        const cursorPos = editor.getPosition()

        if(!cursorPos){
            return false;
        }

        // get the absolute index in the string
        const absoluteOffset = editor.getModel()?.getOffsetAt(cursorPos) ?? 0

        // if the previous token is {{ it means we are in a pebble block -> true
        // if a }} comes after the {{ we have come out of the block and are not -> false
        // if both are empty, they both return -1 -> false
        return editorValue.lastIndexOf("{{", absoluteOffset) > editorValue.lastIndexOf("}}", absoluteOffset);
    }
</script>

<script setup lang="ts">
    import {
        computed,
        h,
        inject,
        onBeforeUnmount,
        onMounted,
        ref,
        render,
        shallowRef,
        VNode,
        watch
    } from "vue";

    import "monaco-editor/esm/vs/editor/editor.all";
    import "monaco-editor/esm/vs/editor/standalone/browser/inspectTokens/inspectTokens";
    import "monaco-editor/esm/vs/editor/standalone/browser/iPadShowKeyboard/iPadShowKeyboard";
    import "monaco-editor/esm/vs/editor/standalone/browser/quickAccess/standaloneCommandsQuickAccess";
    import "monaco-editor/esm/vs/language/json/monaco.contribution";
    import "monaco-editor/esm/vs/basic-languages/monaco.contribution";

    import {editor} from "monaco-editor/esm/vs/editor/editor.api";
    import configureLanguage from "../../composables/monaco/languages/languagesConfigurator";

    import {EDITOR_HIGHLIGHT_INJECTION_KEY, EDITOR_WRAPPER_INJECTION_KEY} from "../no-code/injectionKeys";

    import {STATES, TaskIcon} from "@kestra-io/ui-libs";

    import uniqBy from "lodash/uniqBy";
    import {useI18n} from "vue-i18n";
    import {ElDatePicker} from "element-plus";
    import moment, {Moment} from "moment";
    import PlaceholderContentWidget from "../../composables/monaco/PlaceholderContentWidget";
    import Utils from "../../utils/utils";
    import {hashCode} from "../../utils/global";
    import ICodeEditor = editor.ICodeEditor;
    import debounce from "lodash/debounce";
    import {usePluginsStore} from "../../stores/plugins";
    import {useFlowStore} from "../../stores/flow";
    import EditorType = editor.EditorType;
    import {useRoute} from "vue-router";

    const {t} = useI18n();

    const textAreaValue = computed({
        get() {
            return props.value;
        },
        set(value) {
            emit("change", value);
        }
    });

    const route = useRoute();

    const highlightLine = () => {
        if(!route?.query.highlight) return;

        const editor = getModifiedEditor();

        if (!editor) return;

        editor.focus();

        const lines = editor.getModel()!.getLinesContent();

        let lineNumber = 0;

        for (let i = 0; i < lines.length; i++) {
            if (lines[i].includes(route.query.highlight as string)) {
                lineNumber = i + 1; // Monaco line numbers are 1-based
                break;
            }
        }

        const endLineCharacter = editor?.getModel()!.getLineMaxColumn(lineNumber) ?? 0

        editor.setSelection(new monaco.Range(lineNumber, 0, lineNumber, endLineCharacter));
        editor.revealLineInCenter(lineNumber);
    }

    const highlight = inject(EDITOR_HIGHLIGHT_INJECTION_KEY, ref());
    const isInFlowEditor = inject(EDITOR_WRAPPER_INJECTION_KEY, false);

    watch(highlight, (line) => {
        if (!line) return;

        const editor = getModifiedEditor();

        if (!editor) return;

        editor.focus();

        const end = editor?.getModel()?.getLineMaxColumn(line) ?? 0
        editor.setSelection(new monaco.Range(line, 0, line, end));
    });

    const themes: Record<string, editor.IStandaloneThemeData> = {
        dark: {
            base: "vs-dark",
            inherit: true,
            rules: [
                {token: "", background: "161822"},
            ],
            colors: {
                "minimap.background": "#161822",
                "diffEditor.insertedLineBackground": "#029E734D",
            }
        },
        light: {
            base: "vs",
            inherit: true,
            rules: [
                {token: "type", foreground: "#8405FF"},
                {token: "string.yaml", foreground: "#001233"},
                {token: "comment", foreground: "#8d99ae", fontStyle: "italic"},
            ],
            colors: {
                "editor.lineHighlightBackground": "#fbfaff",
                "editorLineNumber.foreground": "#444444",
                "editor.selectionBackground": "#E8E5FF",
                "editor.wordHighlightBackground": "#E8E5FF",
                "diffEditor.insertedLineBackground": "#029E734D",
            }
        }
    };

    // avoid using browser libs in ts worker added by default
    if (monaco.languages.typescript) {
        monaco.languages.typescript.typescriptDefaults.setCompilerOptions({
            target: monaco.languages.typescript.ScriptTarget.ES2020,
            lib: ["es2020"], // Exclude 'dom' to remove browser types
            allowNonTsExtensions: true
        });
    }

    const props = withDefaults(defineProps<{
        path?: string,
        original?: string,
        value: string,
        theme?: string | (Omit<Partial<editor.IStandaloneThemeData>, "base"> & { base: ThemeBase }),
        language?: string,
        extension?: string,
        options?: EditorOptions,
        schemaType?: string,
        diffEditor?: boolean,
        input?: boolean,
        creating?: boolean,
        suggestionsOnFocus?: boolean,
        readonly?: boolean,
        largeSuggestions?: boolean,
        placeholder?: string,
    }>(), {
        path: "",
        original: "",
        theme: "light",
        diffEditor: false,
        input: false,
        creating: false,
        language: undefined,
        extension: undefined,
        options: undefined,
        schemaType: undefined,
        suggestionsOnFocus: false,
        largeSuggestions: true,
        placeholder: undefined,
    })

    Object.entries(themes).forEach(([themeKey, themeData]) => {
        monaco.editor.defineTheme(themeKey, themeData);
    });

    function defineCustomTheme(theme: Omit<Partial<editor.IStandaloneThemeData>, "base"> & { base: ThemeBase }) {
        const kestraBaseTheme = themes[theme.base];
        const base: Partial<editor.IStandaloneThemeData> & { base: editor.BuiltinTheme } = kestraBaseTheme
            ? {
                ...kestraBaseTheme,
                ...theme,
                rules: [...(kestraBaseTheme.rules ?? []), ...(theme.rules ?? [])],
                base: kestraBaseTheme.base
            }
            : theme as Partial<editor.IStandaloneThemeData> & { base: editor.BuiltinTheme };

        const themeId = hashCode(JSON.stringify(theme)).toString();
        monaco.editor.defineTheme(themeId, {
            inherit: true,
            rules: [],
            colors: {},
            ...base
        });

        return themeId;
    }

    const themeKey = computed(() => {
        if (typeof props.theme === "string") {
            return props.theme;
        }

        return defineCustomTheme(props.theme);
    });

    let localEditor = shallowRef<monaco.editor.IStandaloneCodeEditor | undefined>();
    let localDiffEditor = shallowRef<monaco.editor.IStandaloneDiffEditor | undefined>();

    const suggestWidgetResizeObserver = ref<MutationObserver>()
    const suggestWidgetObserver = ref<MutationObserver>()
    const suggestWidget = ref<HTMLElement>()
    const resizeObserver = ref<ResizeObserver>()

    defineExpose({
        focus,
        destroy,
        monaco,
    })

    const editorResolved = computed(() => {
        return props.diffEditor ? localDiffEditor.value : localEditor.value;
    })

    const emit = defineEmits<{
        (e:"editorDidMount", editor?: typeof editorResolved.value): void,
        (e:"change", value: string, event?: editor.IModelContentChangedEvent): void,
        (e: "mouseMove", event: monaco.editor.IEditorMouseEvent): void;
        (e: "mouseLeave", event: monaco.editor.IPartialEditorMouseEvent): void;
    }>()

    const editorRef = ref<HTMLDivElement | null>(null);

    watch(() => props.path, (newValue, oldValue) => {
        if (newValue !== oldValue) {
            changeTab(newValue, () => Promise.resolve(props.value));
        }
    });

    watch(() => props.options, (newValue, oldValue) => {
        if (editorResolved.value && needReload(newValue, oldValue)) {
            reload();
        } else {
            localEditor.value?.updateOptions(newValue ?? {});
        }
    }, {deep: true});

    watch(() => props.value, (newValue) => {
        if (localEditor.value) {
            const modifiedEditor = getModifiedEditor();
            if (newValue !== modifiedEditor?.getValue()) {
                modifiedEditor?.setValue(newValue);
            }
        }
    });

    watch(() => props.original, (newValue) => {
        if (localEditor.value && props.diffEditor) {
            const originalEditor = getOriginalEditor();
            if (newValue !== originalEditor?.getValue()) {
                originalEditor?.setValue(newValue);
            }
        }
    });

    watch(() => props.theme, (newTheme) => {
        if (typeof newTheme === "object") {
            const themeId = defineCustomTheme(newTheme);

            if (editorResolved.value) {
                monaco.editor.setTheme(themeId);
            }
        } else if (typeof newTheme === "string") {
            if (editorResolved.value) {
                monaco.editor.setTheme(newTheme);
            }
        }
    }, {deep: true});

    const nowMoment: Moment = moment().startOf("day");
    function addedSuggestRows(mutations: MutationRecord[]) {
        return mutations.flatMap(({addedNodes}) => {
            const nodes = [...addedNodes];
            const maybeRows = nodes.filter((n) => (n as HTMLElement).classList?.contains("monaco-list-row"));

            for (let node of nodes) {
                let maybeRow = null;
                if (node instanceof Text) {
                    maybeRow = node.parentElement?.closest(".monaco-list-row");
                }

                if (maybeRow !== null) {
                    return [...maybeRows, maybeRow];
                }
            }

            return maybeRows;
        }) as HTMLElement[];
    }

    const KESTRA_ICON_WRAPPER_CLASS = "kestra-icon-wrapper";
    const replaceRowIcon = (vsCodeIcon: HTMLElement, iconVNode: VNode) => {
        vsCodeIcon.style.display = "none";

        const tempContainer = document.createElement("div");
        render(h("div", {
            class: `${KESTRA_ICON_WRAPPER_CLASS} d-flex align-items-center me-1`,
        }, iconVNode), tempContainer);

        vsCodeIcon.after(tempContainer.firstElementChild!)
        tempContainer.remove();
    }

    const replaceRowsIcons = (nodes: HTMLElement[]) => {
        nodes = uniqBy(nodes, node => node.id);

        for (let node of nodes) {
            const completionValue = node?.getAttribute("aria-label");
            if (!completionValue || node.getAttribute("data-index") === null) {
                continue;
            }

            const vsCodeIcon = node.querySelector(".suggest-icon") as HTMLElement;
            node.querySelector(`.${KESTRA_ICON_WRAPPER_CLASS}`)?.remove();

            if (completionValue.includes(".") && !completionValue.includes("{")) {
                if (pluginsStore?.icons?.[completionValue] !== undefined) {
                    replaceRowIcon(vsCodeIcon, h(TaskIcon, {
                        cls: completionValue,
                        "only-icon": true,
                        icons: pluginsStore.icons,
                    }));
                }
            } else if (STATES[completionValue] !== undefined) {
                replaceRowIcon(vsCodeIcon, h(STATES[completionValue].icon));
            } else {
                vsCodeIcon.style.display = "";
            }
        }
    };

    const selectedDate = ref<Date>(nowMoment.toDate());
    const datePickerWrapper = ref<HTMLElement>();
    const datePicker = ref<typeof ElDatePicker>();
    const datePickerShown = ref(false);
    let datePickerWidget: editor.IContentWidget;

    const datePickerCallback = () => {
        if (editorResolved.value?.getEditorType() !== editor.EditorType.ICodeEditor) {
            return;
        }

        const asCodeEditor = editorResolved.value as editor.ICodeEditor;
        const model: editor.ITextModel = asCodeEditor.getModel()!;
        const position = asCodeEditor.getPosition()!;
        const wordAtPosition = model.getWordAtPosition(position);

        asCodeEditor.focus();
        model.pushEditOperations(
            asCodeEditor.getSelections(),
            [{
                range: {
                    startLineNumber: position?.lineNumber,
                    startColumn: position?.column,
                    endLineNumber: position?.lineNumber,
                    endColumn: wordAtPosition?.endColumn ?? position?.column
                },
                // We don't use the selectedDate directly because if user modifies the input value directly it doesn't work otherwise
                text: `${moment(
                    datePicker.value!.$el.nextElementSibling.querySelector("input").value
                ).toISOString(true)} `,
                forceMoveMarkers: true
            }],
            () => null
        );

        selectedDate.value = nowMoment.toDate();

        if (props.suggestionsOnFocus) {
            asCodeEditor.trigger("datePickerCallback", "editor.action.triggerSuggest", {});
        }
    }

    function removeDatePicker(codeEditor: ICodeEditor) {
        if (
            !datePickerShown.value
        ) {
            return;
        }

        datePickerShown.value = false;
        codeEditor.removeContentWidget(datePickerWidget);
    }

    watch(suggestWidget, async (newVal) => {
        const asCodeEditor = editorResolved.value?.getEditorType() === EditorType.ICodeEditor ? editorResolved.value as editor.ICodeEditor : undefined;

        if (newVal !== undefined) {
            if (newVal.querySelector(".monaco-list-row") !== null) {
                replaceRowsIcons([...newVal.getElementsByClassName("monaco-list-row")] as HTMLElement[]);
            }

            suggestWidgetObserver.value?.disconnect();
            suggestWidgetObserver.value = undefined;


            suggestWidgetObserver.value = new MutationObserver(mutations => {
                mutations.forEach(({removedNodes}) => {
                    if ([...removedNodes.values()].some(n => n instanceof Text && n.textContent === "_DATE_PICKER_")) {
                        if (asCodeEditor !== undefined) {
                            removeDatePicker(asCodeEditor);
                        }
                    }
                })

                const addedRows = addedSuggestRows(mutations);
                replaceRowsIcons(
                    addedRows.filter(row => row.ariaLabel !== "_DATE_PICKER_")
                );

                addedRows.forEach(async row => {
                    if (asCodeEditor !== undefined && row.ariaLabel === "_DATE_PICKER_") {
                        (asCodeEditor.getContribution("editor.contrib.suggestController") as unknown as {
                            cancelSuggestWidget: () => void
                        }).cancelSuggestWidget()

                        if (!datePickerShown.value) {
                            datePickerShown.value = true;
                            if (datePickerWidget === undefined) {
                                datePickerWidget = {
                                    allowEditorOverflow: true,
                                    getId() {
                                        return "kestra_date_picker";
                                    },
                                    getDomNode() {
                                        return datePickerWrapper.value!;
                                    },
                                    getPosition() {
                                        return {
                                            position: asCodeEditor.getPosition(),
                                            preference: [editor.ContentWidgetPositionPreference.BELOW, editor.ContentWidgetPositionPreference.ABOVE]
                                        };
                                    },
                                };
                            }

                            await asCodeEditor.addContentWidget(datePickerWidget);
                            datePicker.value!.handleOpen();
                            setTimeout(() => {
                                datePicker.value!.focus();
                            });
                        }
                    }
                })
            });

            suggestWidgetObserver.value.observe(newVal, {childList: true, subtree: true});

            asCodeEditor?.onDidChangeCursorPosition(() => {
                removeDatePicker(asCodeEditor);
            })
        }
    });

    const disposeCompletions = ref<() => void>();

    const pluginsStore = usePluginsStore();
    const flowStore = useFlowStore();

    const prefix = computed(() => props.schemaType ? `${props.schemaType}-` : "");
    onMounted(async function () {
        await document.fonts.ready;
        await initMonaco();

        if (props.language !== undefined) {
            await configureLanguage(
                flowStore,
                pluginsStore,
                t,
                props.diffEditor ? undefined : editorResolved.value as ICodeEditor,
                props.language,
                props.schemaType
            );
        }

        // Exposing functions globally for testing purposes
        (window as any).pasteToEditor = (textToPaste: string) => {
            localEditor.value?.executeEdits("", [{
                range: localEditor.value?.getSelection() ?? new monaco.Range(0, 0, 0, 0),
                text: textToPaste
            }])
        };
        (window as any).clearEditor = () => {
            localEditor.value?.getModel()?.setValue("")
        };
        (window as any).acceptSuggestion = () => {
            localEditor.value?.trigger("acceptSelectedSuggestion", "acceptSelectedSuggestion", {});
        };
        (window as any).nextSuggestion = () => {
            localEditor.value?.trigger("selectNextSuggestion", "selectNextSuggestion", {});
        };
    })

    onBeforeUnmount(function () {
        destroy();
    })

    function disposeObservers() {
        const swio = suggestWidgetResizeObserver.value
        if (swio !== undefined) {
            swio!.disconnect();
            suggestWidgetResizeObserver.value = undefined;
        }
        suggestWidget.value = undefined;
    }

    /**
     * Goal of this method is to add an observer on the suggest widget to auto-resize it to fit tasks without ellipsis at first appearance.
     * It's using a MutationObserver. The observer expects two scenario:
     *
     *  - `target` is looked at. If it's a Sash (VSCode resizer handle) and it's not disabled (which is the case while loading schema),
     *  it manipulates it through MouseEvents to resize the suggest window. If it's disabled it returns and wait for the next pass while watching class changes
     *  - otherwise, addedNodes is looked at. In that case we are watching for any new children of the global vscode widget handler. The goal is to detect the sash addition
     *  because it's not there at startup. Once detected, if it's disabled it changes the observer to target the Sash (see above) but watching the class to detect `disabled` class removal.
     *  If the Sash is not disabled, we resize directly.
     *
     *  Once the resize has been done, the observer is disconnected and put back to undefined so that new instances of Monaco repeats the process to target the proper DOM element.
     */
    function observeAndResizeSuggestWidget() {
        if (suggestWidgetResizeObserver.value !== undefined) {
            return;
        }

        suggestWidgetResizeObserver.value = new MutationObserver(([{
            target,
            addedNodes
        }]) => {
            const simulateResizeOnSashAndDisconnect = (resizer: HTMLElement) => {
                // If the prop value is passed as false, we don't want to resize the suggest widget
                if(!props.largeSuggestions) return;

                suggestWidgetResizeObserver.value?.disconnect();
                suggestWidgetResizeObserver.value = undefined;

                const resizerInitialCoordinates = {
                    x: resizer.getBoundingClientRect().left,
                    y: resizer.getBoundingClientRect().top
                };

                resizer.dispatchEvent(new MouseEvent("mouseenter", {
                    bubbles: true,
                    clientX: resizerInitialCoordinates.x,
                    clientY: resizerInitialCoordinates.y
                }));
                resizer.dispatchEvent(new MouseEvent("mouseover", {
                    bubbles: true,
                    clientX: resizerInitialCoordinates.x,
                    clientY: resizerInitialCoordinates.y
                }));
                resizer.dispatchEvent(new MouseEvent("mousedown", {
                    bubbles: true,
                    clientX: resizerInitialCoordinates.x,
                    clientY: resizerInitialCoordinates.y
                }));
                resizer.dispatchEvent(new MouseEvent("mousemove", {
                    bubbles: true,
                    clientX: resizerInitialCoordinates.x + 80,
                    clientY: resizerInitialCoordinates.y
                }));
                resizer.dispatchEvent(new MouseEvent("mouseup", {
                    bubbles: true,
                    clientX: resizerInitialCoordinates.x + 80,
                    clientY: resizerInitialCoordinates.y
                }));
                resizer.dispatchEvent(new MouseEvent("mouseout", {
                    bubbles: true,
                    clientX: resizerInitialCoordinates.x + 80,
                    clientY: resizerInitialCoordinates.y
                }));
                resizer.dispatchEvent(new MouseEvent("mouseleave", {
                    bubbles: true,
                    clientX: resizerInitialCoordinates.x + 80,
                    clientY: resizerInitialCoordinates.y
                }));
            }

            const targetHtmlElement = target as HTMLElement;
            if (targetHtmlElement.classList.contains("monaco-sash")) {
                if (!targetHtmlElement.classList.contains("disabled")) {
                    simulateResizeOnSashAndDisconnect(targetHtmlElement);
                }

                return;
            }

            const maybeSuggestWidgetHtmlElement = addedNodes?.[0] as HTMLElement;
            if (maybeSuggestWidgetHtmlElement?.classList.contains("suggest-widget")) {
                suggestWidget.value = maybeSuggestWidgetHtmlElement;
                const resizer = maybeSuggestWidgetHtmlElement.querySelector(".monaco-sash.vertical") as HTMLElement;

                if (resizer.classList.contains("disabled")) {
                    suggestWidgetResizeObserver.value!.disconnect();
                    suggestWidgetResizeObserver.value?.observe(resizer, {attributeFilter: ["class"]})
                } else {
                    simulateResizeOnSashAndDisconnect(resizer);
                }
            }
        });

        const $el = editorRef.value
        if ($el !== null) {
            const modifiedEditorWidgets = $el.querySelector(".editor.modified .overflowingContentWidgets");
            const el = modifiedEditorWidgets ?? $el.querySelector(".overflowingContentWidgets")
            if(el){
                suggestWidgetResizeObserver.value.observe(el, {childList: true})
            }
        }
    }

    async function initMonaco() {
        let options: EditorOptions = {
            value: props.value,
            theme: themeKey.value,
            language: props.language,
            suggest: {
                showClasses: false,
                showWords: false
            },
            ...(isInFlowEditor ? {
                padding: {
                    top: 16
                }
            } : {}),
            ...props.options
        };

        if (props.diffEditor) {
            if (editorRef.value) {
                localDiffEditor.value = monaco.editor.createDiffEditor(editorRef.value, {
                    ...options,
                    ignoreTrimWhitespace: false
                });
                let originalModel = monaco.editor.createModel(
                    props.original,
                    props.language,
                    monaco.Uri.file(prefix.value + Utils.uid() + (props.language ? `.${props.language}` : ""))
                );
                let modifiedModel = monaco.editor.createModel(
                    props.value,
                    props.language,
                    monaco.Uri.file(prefix.value + Utils.uid() + (props.language ? `.${props.language}` : ""))
                );
                localDiffEditor.value.setModel({
                    original: originalModel,
                    modified: modifiedModel
                });
                let modifiedBackspaceTimeout: number | null = null;

                const modifiedEditor = localDiffEditor.value.getModifiedEditor();
                modifiedEditor.onKeyDown((e) => {
                    if (e.keyCode === monaco.KeyCode.Backspace) {
                        if (modifiedBackspaceTimeout) clearTimeout(modifiedBackspaceTimeout);

                        if(!isCursorInPebbleBlock(modifiedEditor)) {
                            return;
                        }

                        modifiedBackspaceTimeout = window.setTimeout(() => {
                            modifiedEditor.trigger("keyboard", "editor.action.triggerSuggest", {});
                        }, 250);
                    }
                });
            }
        } else {
            monaco.editor.addKeybindingRule({
                keybinding: monaco.KeyMod.CtrlCmd | monaco.KeyCode.Space,
                command: "editor.action.triggerSuggest"
            })

            monaco.editor.addKeybindingRule({
                keybinding: monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyP,
                command: "editor.action.quickCommand"
            })

            monaco.editor.addKeybindingRule({
                keybinding: monaco.KeyMod.CtrlCmd | monaco.KeyCode.DownArrow,
                command: "editor.action.fontZoomOut",
                when: "editorFocus"
            })

            monaco.editor.addKeybindingRule({
                keybinding: monaco.KeyMod.CtrlCmd | monaco.KeyCode.UpArrow,
                command: "editor.action.fontZoomIn",
                when: "editorFocus"
            })

            monaco.editor.addKeybindingRule({
                keybinding: monaco.KeyMod.CtrlCmd | monaco.KeyCode.Digit0,
                command: "editor.action.fontZoomReset",
                when: "editorFocus"
            });

            if (editorRef.value) {
                localEditor.value = monaco.editor.create(editorRef.value, {
                    ...options,
                    fixedOverflowWidgets: true // Helps suggestion widget render above other elements
                });
                let localBackspaceTimeout: number | null = null;

                localEditor.value.onKeyDown((e) => {
                    if (e.keyCode === monaco.KeyCode.Backspace) {
                        if (localBackspaceTimeout) clearTimeout(localBackspaceTimeout);

                        if(!localEditor.value || !isCursorInPebbleBlock(localEditor.value)) {
                            return;
                        }

                        localBackspaceTimeout = window.setTimeout(() => {
                            localEditor.value?.trigger("keyboard", "editor.action.triggerSuggest", {});
                        }, 250);
                    }
                });
                if (props.suggestionsOnFocus) {
                    localEditor.value.onMouseDown(() => {
                        localEditor.value!.trigger("click", "editor.action.triggerSuggest", {});
                    });
                }

                if (props.placeholder !== undefined) {
                    new PlaceholderContentWidget(props.placeholder, localEditor.value);
                }

                const suggestController = localEditor.value!.getContribution("editor.contrib.suggestController") as unknown as {
                    model: { state: 0 | 1 | 2 },
                    cancelSuggestWidget: () => void
                };

                localEditor.value.onDidChangeModelContent(e => {
                    if ((e.isUndoing || e.isRedoing) && suggestController.model.state !== 0) {
                        suggestController.cancelSuggestWidget();
                        localEditor.value!.trigger("refreshSuggestionsAfterUndoRedo", "editor.action.triggerSuggest", {});
                    }
                });

                localEditor.value.onDidChangeCursorPosition(debounce(() => {
                    if (suggestController.model.state !== 0) {
                        suggestController.cancelSuggestWidget();
                        localEditor.value!.trigger("refreshSuggestionsOnCursorMove", "editor.action.triggerSuggest", {});
                    }
                }, 300))

                localEditor.value.onMouseMove((e) => {
                    emit("mouseMove", e);
                });

                localEditor.value.onMouseLeave((e) => {
                    emit("mouseLeave", e);
                });
            }

            if (!props.input) {
                await changeTab(props.path, () => Promise.resolve(props.value), false);
            }
        }

        let _editor = getModifiedEditor();
        _editor?.onDidChangeModelContent(function (event) {
            let value = _editor.getValue();

            if (props.value !== value) {
                emit("change", value, event);
            }
        });

        observeAndResizeSuggestWidget();

        setTimeout(() => monaco.editor.remeasureFonts(), 1)
        emit("editorDidMount", editorResolved.value);

        /* Hhandle resizing. */
        resizeObserver.value = new ResizeObserver(() => {
            if (localEditor.value) {
                localEditor.value.layout();
            }
            if (localDiffEditor.value) {
                localDiffEditor.value.getModifiedEditor().layout();
                localDiffEditor.value.getOriginalEditor().layout();
            }
        });
        if (editorRef.value) {
            resizeObserver.value.observe(editorRef.value);
        }

        highlightLine();
    }

    async function changeTab(pathOrName: string, valueSupplier: () => Promise<string>, useModelCache = true) {
        let model;
        if (props.input || pathOrName === undefined) {
            model = monaco.editor.createModel(
                await valueSupplier(),
                props.language,
                monaco.Uri.file(prefix.value + Utils.uid() + (props.language ? `.${props.language}` : ""))
            );
        } else {
            if (!pathOrName.includes(".") && props.language) {
                pathOrName = `${pathOrName}.${props.language}`;
            }
            const fileUri = monaco.Uri.file(prefix.value + pathOrName);
            model = monaco.editor.getModel(fileUri);
            if (model === null) {
                model = monaco.editor.createModel(
                    await valueSupplier(),
                    props.language,
                    fileUri
                );
            } else if (!useModelCache) {
                model.setValue(await valueSupplier());
            }
        }
        localEditor.value?.setModel(model);

        return model
    }

    function getModifiedEditor() {
        return props.diffEditor ? localDiffEditor.value?.getModifiedEditor() : localEditor.value;
    }

    function getOriginalEditor() {
        return props.diffEditor ? localDiffEditor.value?.getOriginalEditor() : localEditor.value;
    }

    function focus() {
        editorResolved.value?.focus();
    }

    watch(() => props.diffEditor, () => {
        reload();
    });

    watch(() => props.value , (newVal) => {
        if (props.diffEditor && localDiffEditor.value?.getModel()?.modified?.getValue?.() !== newVal) {
            localDiffEditor.value?.getModel()?.modified?.setValue?.(newVal);
        }
    });

    function destroy() {
        disposeObservers();
        disposeCompletions.value?.();
        resizeObserver.value?.disconnect();
        resizeObserver.value = undefined;
        if (localDiffEditor.value !== undefined) {
            localDiffEditor.value?.dispose();
            localDiffEditor.value?.getModel()?.modified?.dispose();
            localDiffEditor.value?.getModel()?.original?.dispose();
            localDiffEditor.value = undefined;
        }
        if (localEditor.value !== undefined) {
            localEditor.value?.dispose();
            localEditor.value?.getModel()?.dispose();
            localEditor.value = undefined
        }
    }

    function needReload(newValue?: { renderSideBySide?: boolean }, oldValue?: { renderSideBySide?: boolean }) {
        return oldValue?.renderSideBySide !== newValue?.renderSideBySide;
    }

    function reload() {
        destroy();
        initMonaco();
    }
</script>

<style scoped lang="scss">
    @import "../../styles/layout/root-dark";
    .ks-monaco-editor {
        position: absolute;
        width: 100%;
        height: 100%;
        outline: none;
    }

    .main-editor > #editorWrapper .monaco-editor {
        padding: 1rem 0 0 1rem;
    }

    .custom-dark-vs-theme .ks-monaco-editor :deep(.sticky-widget) {
        background-color: var(--ks-background-input);
    }

    .monaco-editor {
        :deep(.monaco-scrollable-element) {
            > .scrollbar {
                .slider {
                    width: 13px !important;
                    background: var(--ks-border-primary) !important;
                    border-radius: 8px !important;
                    border: 4px solid var(--ks-background-body) !important;
                }
            }

            .monaco-list-row[aria-label="_DATE_PICKER_"] {
                padding-right: 0 !important;
            }
        }
    }
</style>