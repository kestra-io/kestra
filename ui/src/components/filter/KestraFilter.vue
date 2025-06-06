<template>
    <section class="d-flex flex-column mb-3 w-100">
        <div class="d-flex">
            <Items :prefix="itemsPrefix" @search="handleClickedItems" />

            <MonacoEditor
                ref="monacoEditor"
                class="border flex-grow-1 position-relative"
                :language="`${language.domain === undefined ? '' : (language.domain + '-')}${legacyQuery ? 'legacy-' : ''}filter`"
                :schema-type="language.domain"
                :value="filter"
                @change="filter = $event"
                :theme="themeComputed"
                :options="options"
                @editor-did-mount="editorDidMount"
                suggestions-on-focus
                :placeholder="placeholder ?? t('filters.label')"
                data-testid="monaco-filter"
            />
            <el-button-group
                class="d-inline-flex"
                :class="{
                    'me-1':
                        buttons.refresh.shown ||
                        settings.shown ||
                        dashboards.shown ||
                        properties.shown,
                }"
            >
                <Save
                    :disabled="filter.length === 0"
                    :prefix="itemsPrefix"
                    :current="filter"
                />
            </el-button-group>

            <el-button-group
                v-if="buttons.refresh.shown || settings.shown"
                class="d-inline-flex ms-1"
                :class="{'me-1': dashboards.shown || properties.shown}"
            >
                <RefreshButton
                    v-if="buttons.refresh.shown"
                    @refresh="buttons.refresh.callback"
                />
                <Settings
                    v-if="settings.shown"
                    :settings="settings"
                    :refresh="buttons.refresh.shown"
                />
            </el-button-group>

            <Dashboards
                v-if="dashboards.shown"
                @dashboard="(value) => emits('dashboard', value)"
                class="ms-1"
            />
            <Properties
                v-if="properties.shown"
                :columns="properties.columns"
                :model-value="properties.displayColumns"
                :storage-key="properties.storageKey"
                @update-properties="(v: Property['displayColumns']) => emits('updateProperties', v)"
                class="ms-1"
            />
        </div>
    </section>
</template>

<script setup lang="ts">
    import MonacoEditor, {ThemeBase} from "../inputs/MonacoEditor.vue";
    import {computed, getCurrentInstance, ref, Ref, watch} from "vue";
    import Utils, {useTheme} from "../../utils/utils";
    import {Buttons, Property, Shown} from "./utils/types";
    import {editor} from "monaco-editor/esm/vs/editor/editor.api";
    import Items from "./segments/Items.vue";
    import {cssVariable} from "@kestra-io/ui-libs";
    import {LocationQuery, useRoute, useRouter} from "vue-router";
    import Save from "./segments/Save.vue";
    import Settings from "./segments/Settings.vue";
    import RefreshButton from "../layout/RefreshButton.vue";
    import Dashboards from "./segments/Dashboards.vue";
    import Properties from "./segments/Properties.vue";
    import {COMPARATORS_REGEX} from "../../composables/monaco/languages/filters/filterLanguageConfigurator.ts";
    import {Comparators, getComparator} from "../../composables/monaco/languages/filters/filterCompletion.ts";
    import {watchDebounced} from "@vueuse/core";
    import {FilterLanguage} from "../../composables/monaco/languages/filters/filterLanguage.ts";
    import DefaultFilterLanguage from "../../composables/monaco/languages/filters/impl/defaultFilterLanguage.ts";

    const router = useRouter();
    const route = useRoute();
    const t = getCurrentInstance()!.appContext.config.globalProperties.$t;

    const props = withDefaults(defineProps<{
        prefix?: string | undefined;
        language?: FilterLanguage,
        propertiesWidth?: number,
        buttons?: (Omit<Buttons, "settings"> & {
            settings: Omit<Buttons["settings"], "charts"> & { charts?: Buttons["settings"]["charts"] }
        }),
        dashboards?: Shown,
        properties?: Property,
        readOnly?: boolean,
        placeholder?: string | undefined,
        queryNamespace?: string,
        legacyQuery?: boolean,
    }>(), {
        prefix: undefined,
        language: () => DefaultFilterLanguage,
        propertiesWidth: 144,
        buttons: () => ({
            refresh: {
                shown: false,
                callback: () => {
                }
            },
            settings: {
                shown: false,
                charts: {
                    shown: false,
                    value: false,
                    callback: () => {
                    }
                }
            }
        }),
        dashboards: () => ({
            shown: false
        }),
        properties: () => ({
            shown: false
        }),
        readOnly: false,
        placeholder: undefined,
        queryNamespace: undefined,
        legacyQuery: false
    });

    const skipRouteWatcherOnce = ref(false);

    const settings = computed(() => ({
        ...props.buttons.settings,
        charts: props.buttons.settings.charts ?? {
            shown: false,
            value: false,
            callback: () => {
            }
        }
    }));

    const itemsPrefix = computed(() => props.prefix ?? route.name?.toString());

    const emits = defineEmits(["dashboard", "updateProperties"]);

    const filter = ref<string>("");

    const queryRemapper: Record<string, string> = {
        q: "text"
    };
    const reversedQueryRemapper: Record<string, string> = Object.fromEntries(
        Object.entries(queryRemapper)
            .map(([key, value]) => [value, key])
    );

    const EXCLUDED_QUERY_FIELDS = ["sort", "size", "page"];

    const filteredRouteQuery = computed(() => route.query === undefined
        ? undefined
        : Object.fromEntries(Object.entries(route.query).filter(([key]) => !EXCLUDED_QUERY_FIELDS.includes(key))) as LocationQuery
    );

    watch(filteredRouteQuery, (newVal) => {
        if (skipRouteWatcherOnce.value) {
            skipRouteWatcherOnce.value = false;
            return;
        }

        if (!newVal) {
            return;
        }

        let query = newVal;
        if (props.queryNamespace !== undefined) {
            query = Object.fromEntries(
                Object.entries(newVal)
                    .filter(([key]) => {
                        return key.startsWith(props.queryNamespace + "[");
                    })
                    .map(([key, value]) =>
                        // We trim the queryNamespace from the key
                        ([key.substring(props.queryNamespace!.length + 2, key.length - 1), value])
                    )
            );
        }

        if (props.legacyQuery) {
            /*
            TODO Known issue: the autocompletion for legacy queries is filling the first comparator found while the query retrieval is using EQUALS
            Handling that would require passing the language filter here which we don't want so we'll just wait for the full filters migration
             */
            filter.value = Object.entries(query)
                .flatMap(([key, values]) => {
                    if (!Array.isArray(values)) {
                        values = [values];
                    }

                    return values.map(value => (queryRemapper?.[key] ?? key) + Comparators.EQUALS + value);
                }).join(" ");
        } else {
            filter.value = Object.entries(query)
                .filter(([key]) => key.startsWith("filters["))
                .flatMap(([key, values]) => {
                    const [_, filterKey, comparator, subKey] = key.match(/filters\[([^\]]+)]\[([^\]]+)](?:\[([^\]]+)])?/) ?? [];
                    let maybeSubKeyString;
                    if (subKey === undefined) {
                        maybeSubKeyString = "";
                    } else {
                        maybeSubKeyString = "." + (subKey.includes(" ") ? `"${subKey}"` : subKey);
                    }

                    if (!Array.isArray(values)) {
                        values = [values];
                    }

                    return values.map(value => (queryRemapper?.[filterKey] ?? filterKey) + maybeSubKeyString + getComparator(comparator as Parameters<typeof getComparator>[0]) + (value!.includes(" ") ? `"${value}"` : value));
                })
                .join(" ");
        }

        filter.value = filter.value.length > 0 ? (filter.value + " ") : filter.value; // Add a trailing space to allow for autocompletion to work properly
    }, {immediate: true, deep: true});

    const COMPARATOR_LABEL_BY_VALUE: Record<Comparators, keyof typeof Comparators> = Object.fromEntries(
        Object.entries(Comparators)
            .map(([label, value]) => [value, label])
    ) as Record<Comparators, keyof typeof Comparators>;

    type Filter = {
        key: string,
        comparator: keyof typeof Comparators | "IN" | "NOT_IN",
        value: string
    };
    const filterQueryString = computed(() => {
        if (filter.value.length === 0) {
            return {};
        }

        const KEY_MATCHER = "((?:(?!" + COMPARATORS_REGEX + ")(?:\\S|\"[^\"]*\"))+?)";
        const COMPARATOR_MATCHER = "(" + COMPARATORS_REGEX + ")";
        const MAYBE_PREVIOUS_VALUE = "(?:(?<=\\S),)?";
        const VALUE_MATCHER = "((?:" + MAYBE_PREVIOUS_VALUE + "(?:(?:\"[^\"]*\")|(?:[^\\s,]*)))+)";
        const filterMatcher = new RegExp("\\s*(?<!\\S)" +
            "((?:" + KEY_MATCHER + COMPARATOR_MATCHER + VALUE_MATCHER + ")" +
            "|\"([^\"]*)\"" +
            "|((?:(?!" + COMPARATORS_REGEX + ")\\S(?!" + COMPARATORS_REGEX + "))+))" +
            "(?!\\S)\\s*", "g");
        let matches: RegExpExecArray | null;
        const filters: Filter[] = [];
        while ((matches = filterMatcher.exec(filter.value)) !== null) {
            const [_, __, key, comparator, commaSeparatedValues, quotedText, text] = matches as unknown as [string, string, string | undefined, Comparators | undefined, string | undefined, string | undefined, string | undefined];

            // If we're not in a {key}{comparator}{value} format, we assume it's a text search
            if (key === undefined) {
                if (props.language.textFilterSupported && (text === undefined || !props.language.keyMatchers()?.some(keyMatcher => keyMatcher.test(text)))) {
                    filters.push({
                        key: "text",
                        comparator: "EQUALS",
                        value: quotedText ?? text!
                    });
                }
                continue;
            }

            if (!props.language.keyMatchers()?.some(keyMatcher => keyMatcher.test(key))) {
                continue; // Skip keys that don't match the language key matchers
            }

            if (!props.language.comparatorsPerKey()[FilterLanguage.withNestedKeyPlaceholder(key)].some(c => Comparators[c] === comparator)) {
                continue; // Skip comparators that are not valid for the key
            }

            const values = [...new Set(commaSeparatedValues?.split(",")?.filter(value => value !== "")?.map(value => value.replaceAll("\"", "")) ?? [])];
            if (values.length === 0) {
                continue; // Skip empty values
            }

            let comparatorLabel: keyof typeof Comparators | "IN" | "NOT_IN" = COMPARATOR_LABEL_BY_VALUE[comparator as Comparators];
            if (values.length > 1) {
                switch (comparator) {
                case "=": {
                    comparatorLabel = "IN";
                    break;
                }
                case "!=": {
                    comparatorLabel = "NOT_IN";
                    break;
                }
                }
            }

            filters.push({
                key,
                comparator: comparatorLabel,
                value: values.join(",")
            });
        }

        let queryEntries = filters.flatMap(({key: key, comparator: comparator, value: value}) => {
            let queryKey = reversedQueryRemapper?.[key] ?? key;

            if (!props.legacyQuery) {
                if (key.includes(".")) {
                    const keyAndSubKeyMatch = queryKey.match(/([^.]+)\.([^.]+)/);
                    const rootKey = keyAndSubKeyMatch?.[1];
                    const subKey = keyAndSubKeyMatch?.[2].replace(/^"([^"]*)"$/, "$1");
                    if (rootKey === undefined || subKey === undefined) {
                        return [];
                    }

                    return [[`filters[${rootKey}][${comparator}][${subKey}]`, value]];
                }

                queryKey = "filters[" + queryKey + "]";
            } else {
                return [[queryKey, value]];
            }

            return [[`${queryKey}[${comparator}]`, value]];
        });

        if (props.queryNamespace !== undefined) {
            queryEntries = queryEntries.map(([queryKey, value]) => [`${props.queryNamespace}[${queryKey}]`, value]);
        }

        return queryEntries.reduce((acc, [key, value]) => {
            if (acc[key] === undefined) {
                acc[key] = value;
            } else {
                acc[key] = Array.isArray(acc[key]) ? [...acc[key], value] : [acc[key], value];
            }

            return acc;
        }, {} as LocationQuery);
    });

    const handleClickedItems = (value: string | undefined) => {
        if (value) {
            filter.value = value;
        }
    };

    const theme = useTheme();
    const themeComputed: Ref<Omit<Partial<editor.IStandaloneThemeData>, "base"> & { base: ThemeBase }> = ref({
        base: Utils.getTheme()!,
        colors: {
            "editor.background": cssVariable("--ks-background-input")!
        },
        rules: [
            {token: "variable.value", foreground: cssVariable("--ks-badge-content")}
        ]
    });
    watch(theme, () => {
        themeComputed.value = {
            base: Utils.getTheme()!,
            colors: {
                "editor.background": cssVariable("--ks-background-input")!
            },
            rules: [
                {token: "variable.value", foreground: cssVariable("--ks-badge-content")}
            ]
        };

    }, {immediate: true});

    const options: editor.IStandaloneEditorConstructionOptions = {
        lineNumbers: "off",
        folding: false,
        renderLineHighlight: "none",
        wordBasedSuggestions: "off",
        occurrencesHighlight: "off",
        hideCursorInOverviewRuler: true,
        overviewRulerBorder: false,
        overviewRulerLanes: 0,
        lineNumbersMinChars: 0,
        lineHeight: 32,
        fontSize: 16,
        minimap: {
            enabled: false
        },
        scrollBeyondLastLine: false,
        scrollBeyondLastColumn: 0,
        scrollbar: {
            horizontal: "hidden",
            alwaysConsumeMouseWheel: false,
            handleMouseWheel: true,
            horizontalScrollbarSize: 0,
            verticalScrollbarSize: 0,
            useShadows: false
        },
        stickyScroll: {
            enabled: false
        },
        find: {
            addExtraSpaceOnTop: false,
            autoFindInSelection: "never",
            seedSearchStringFromSelection: "never"
        },
        contextmenu: false,
        lineDecorationsWidth: 0,
        automaticLayout: true,
        wordWrap: "on",
        fontFamily: "var(--bs-body-font-family)",
        wrappingStrategy: "advanced",
        readOnly: props.readOnly
    };

    const monacoEditor = ref<typeof MonacoEditor>();

    const editorDidMount = (mountedEditor: editor.IStandaloneCodeEditor) => {
        mountedEditor.onDidContentSizeChange((e) => {
            if (monacoEditor.value === undefined) {
                return;
            }
            monacoEditor.value.$el.style.height =
                e.contentHeight + "px";
        });

        mountedEditor.onDidChangeModelContent(e => {
            if (e.changes.length === 1 && (e.changes[0].text === " " || e.changes[0].text === "\n")) {
                if (mountedEditor.getModel()?.getValue().charAt(e.changes[0].rangeOffset - 1) === ",") {
                    mountedEditor.executeEdits("", [
                        {
                            range: {
                                ...e.changes[0].range,
                                startColumn: e.changes[0].range.startColumn - 1,
                                endColumn: e.changes[0].range.startColumn
                            },
                            text: "",
                            forceMoveMarkers: true
                        }
                    ]);
                }
            }
        });
    };

    watchDebounced(filterQueryString, () => {
        skipRouteWatcherOnce.value = true;
        router.push({
            query: {
                sort: route.query.sort,
                size: route.query.size,
                page: route.query.page,
                ...filterQueryString.value
            }
        });
    }, {immediate: true, debounce: 1000});
</script>

<style lang="scss" scoped>
    @use "@kestra-io/ui-libs/src/scss/variables.scss" as global-var;

    :deep(.ks-monaco-editor) {
        padding-left: 0.75rem;
        padding-right: 0.75rem;
        background-color: var(--ks-background-input);
        border-top-right-radius: var(--el-border-radius-base);
        border-bottom-right-radius: var(--el-border-radius-base);
        min-width: 0;

        .mtk25 {
            background-color: var(--ks-badge-background);
            padding: 2px 6px;
            border-radius: var(--el-border-radius-base);

            &:has(+ .mtk25) {
                padding-right: 0;
                border-top-right-radius: 0;
                border-bottom-right-radius: 0;
            }

            + .mtk25 {
                padding-left: 0;
                border-top-left-radius: 0;
                border-bottom-left-radius: 0;
            }
        }

        .monaco-editor {
            .suggest-widget .monaco-list .monaco-list-row {
                .suggest-icon, .kestra-icon-wrapper > .material-design-icon {
                    display: inline-flex;
                    vertical-align: middle;
                    font-size: 18px;
                }

                @each $status in global-var.$statusList {
                    &[aria-label="#{to-upper-case($status)}"]:not(.focused) {
                        background-color: var(--ks-background-#{$status});
                        border: 1px solid var(--ks-border-#{$status});
                        border-radius: var(--el-border-radius-base);

                        .suggest-icon, .kestra-icon-wrapper, .monaco-highlighted-label {
                            color: var(--ks-content-#{$status});
                        }
                    }
                }
            }

            .view-lines {
                word-spacing: .3ch;
            }

            .view-overlays {
                .snippet-placeholder, .selected-text {
                    height: 20px;
                    top: 50% !important;
                    transform: translateY(-50%);
                }

                .finish-snippet-placeholder {
                    display: none;
                }
            }
        }
    }
</style>
