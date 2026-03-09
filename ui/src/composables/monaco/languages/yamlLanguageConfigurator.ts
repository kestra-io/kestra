import {computed, watch} from "vue";
import {useI18n} from "vue-i18n";
import {configureMonacoYaml} from "monaco-yaml";
import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import {yamlSchemas} from "override/utils/yamlSchemas";
import {StandaloneServices} from "monaco-editor/esm/vs/editor/standalone/browser/standaloneServices";
import {ILanguageFeaturesService} from "monaco-editor/esm/vs/editor/common/services/languageFeatures";
import AbstractLanguageConfigurator from "./abstractLanguageConfigurator";
import {YamlAutoCompletion} from "../../../services/autoCompletionProvider";
import RegexProvider from "../../../utils/regex";
import * as YamlUtils from "@kestra-io/ui-libs/flow-yaml-utils";
import IPosition = monaco.IPosition;
import IDisposable = monaco.IDisposable;
import IModel = monaco.editor.IModel;
import ProviderResult = monaco.languages.ProviderResult;
import CompletionList = monaco.languages.CompletionList;
import {
    endOfWordColumn,
    NO_SUGGESTIONS,
    registerFunctionParametersAutoCompletion,
    registerNestedValueAutoCompletion,
    registerPebbleAutocompletion,
} from "./pebbleLanguageConfigurator";
import {usePluginsStore} from "../../../stores/plugins";
import {useBlueprintsStore} from "../../../stores/blueprints";
import {languages} from "monaco-editor/esm/vs/editor/editor.api";
import CompletionItem = languages.CompletionItem;

type TaskLike = Record<string, unknown>;

function isTaskLike(value: unknown): value is TaskLike {
    return (
        typeof value === "object" &&
        value !== null &&
        typeof (value as TaskLike).id === "string" &&
        typeof (value as TaskLike).type === "string"
    );
}

function filterMissingRequiredTaskProperties({
    source,
    cursorIndex,
    requiredProperties,
}: {
    source: string;
    cursorIndex: number;
    requiredProperties: string[];
}): string[] {
    if (!requiredProperties.length || !source.length) {
        return [];
    }

    try {
        const safeCursorIndex = Math.max(
            0,
            Math.min(cursorIndex - 1, source.length - 1),
        );
        const probeIndexes = [safeCursorIndex];
        let previousNonWhitespace = safeCursorIndex;
        while (
            previousNonWhitespace > 0 &&
            /\s/.test(source.charAt(previousNonWhitespace))
        ) {
            previousNonWhitespace--;
        }
        if (previousNonWhitespace !== safeCursorIndex) {
            probeIndexes.push(previousNonWhitespace);
        }

        for (const probeIndex of probeIndexes) {
            const localized = YamlUtils.localizeElementAtIndex(
                source,
                probeIndex,
            );
            const candidates = [...(localized?.parents ?? []), localized?.value];

            for (let i = candidates.length - 1; i >= 0; i--) {
                const candidate = candidates[i];
                if (
                    isTaskLike(candidate) &&
                    typeof candidate.id === "string" &&
                    typeof candidate.type === "string"
                ) {
                    return requiredProperties.filter(
                        (property) =>
                            !Object.prototype.hasOwnProperty.call(
                                candidate,
                                property,
                            ),
                    );
                }
            }
        }

        return requiredProperties;
    } catch {
        return requiredProperties;
    }
}

export class YamlLanguageConfigurator extends AbstractLanguageConfigurator {
    private readonly _yamlAutoCompletion: YamlAutoCompletion;

    constructor(yamlAutoCompletion: YamlAutoCompletion) {
        super("yaml");
        this._yamlAutoCompletion = yamlAutoCompletion;
    }

    async configureLanguage(pluginsStore: ReturnType<typeof usePluginsStore>) {
        const validateYAML = computed(() => useBlueprintsStore().validateYAML);
        // Keep Monaco YAML validation in sync with the blueprint store setting.
        watch(validateYAML, (shouldValidate) =>
            configureMonacoYaml(monaco, {validate: shouldValidate}),
        );

        // Base YAML language setup shared across all YAML editors.
        configureMonacoYaml(monaco, {
            enableSchemaRequest: true,
            hover: localStorage.getItem("hoverTextEditor") === "true",
            completion: true,
            validate: validateYAML.value ?? true,
            format: true,
            schemas: yamlSchemas(),
        });

        const yamlCompletion = (
            StandaloneServices.get(ILanguageFeaturesService).completionProvider
                ._entries as {
                    selector: string;
                    provider: {
                        provideCompletionItems: (
                            model: IModel,
                            position: IPosition,
                        ) => ProviderResult<CompletionList>;
                    };
                }[]
        ).find((completion) => completion.selector === "yaml");

        if (yamlCompletion === undefined) {
            return;
        }

        const initialCompletion =
            yamlCompletion.provider.provideCompletionItems;

        // Wrap Monaco YAML completion so we can tune ordering and matching behavior
        // for Kestra plugin type values without replacing the default provider.
        yamlCompletion.provider.provideCompletionItems = async function (
            model: IModel,
            position: IPosition,
        ) {
            const defaultCompletion = await initialCompletion(model, position);
            if (!defaultCompletion) {
                return defaultCompletion;
            }

            // ---- Detect "type: <value>" context (only then we enforce "whole word" matching) ----
            const wordUntil = model.getWordUntilPosition(position);
            const typed = (wordUntil?.word ?? "").toLowerCase();

            const line = model.getLineContent(position.lineNumber);
            const beforeWord = line.slice(
                0,
                Math.max((wordUntil?.startColumn ?? position.column) - 1, 0),
            );
            // Matches:
            //   type:
            //   - type:
            const isTypeValueContext = /^\s*(?:-\s*)?type\s*:\s*$/i.test(
                beforeWord,
            );
            // Split plugin class names (`a.b.C`) into lowercase searchable segments.
            const getLabelSegments = (label: string) =>
                label.toLowerCase().split(/\.(?=\w)/).filter(Boolean);
            // Match typed input against any segment, while still preferring the last segment.
            const matchesTypeInput = (label: string, input: string) => {
                if (!input) return true;

                const segments = getLabelSegments(label);
                if (segments.length === 0) return false;

                const last = segments[segments.length - 1];
                if (last.startsWith(input)) return true;
                if (last.includes(input)) return true;

                return segments.slice(0, -1).some((segment) => {
                    return (
                        segment.startsWith(input) || segment.includes(input)
                    );
                });
            };

            const suggestions = (
                defaultCompletion.suggestions as (CompletionItem & {
                    label: string;
                })[]
            )
                // Monaco sometimes truncates labels with `...`; restore full labels when possible.
                .map((suggestion) => {
                    if (
                        suggestion.label.endsWith("...") &&
                        typeof suggestion.insertText === "string" &&
                        suggestion.insertText.includes(
                            suggestion.label.substring(
                                0,
                                suggestion.label.length - 3,
                            ),
                        )
                    ) {
                        return {...suggestion, label: suggestion.insertText};
                    }
                    return suggestion;
                })
                // Hide deprecated plugin classes from completion results.
                .filter((suggestion) => {
                    if (suggestion.label.includes(".")) {
                        return !pluginsStore.deprecatedTypes.includes(
                            suggestion.label,
                        );
                    }
                    return true;
                })
                // Improve ranking and filter text so plugin type lookup feels natural.
                .map((suggestion) => {
                    const wordAtPosition = model
                        .getWordAtPosition(position)
                        ?.word?.toLowerCase();

                    if (wordAtPosition !== undefined) {
                        const sortBumperText = "a1".repeat(10);

                        if (suggestion.label.includes(".")) {
                            const dotSplit = getLabelSegments(suggestion.label);
                            const lastSegment = dotSplit[dotSplit.length - 1];

                            if (lastSegment.startsWith(wordAtPosition)) {
                                suggestion.sortText =
                                    sortBumperText.repeat(5) + suggestion.label;
                            } else if (lastSegment.includes(wordAtPosition)) {
                                suggestion.sortText =
                                    sortBumperText.repeat(4) + suggestion.label;
                            } else {
                                suggestion.sortText =
                                    dotSplit
                                        .splice(dotSplit.length - 1, 1)
                                        .reduceRight((prefix, part) => {
                                            let sortBumperPrefixForPart:
                                                | string
                                                | undefined;

                                            if (
                                                part.startsWith(wordAtPosition)
                                            ) {
                                                sortBumperPrefixForPart =
                                                    sortBumperText.repeat(3);
                                            } else if (
                                                part.includes(wordAtPosition)
                                            ) {
                                                sortBumperPrefixForPart =
                                                    sortBumperText.repeat(2);
                                            }

                                            if (
                                                sortBumperPrefixForPart ===
                                                undefined ||
                                                prefix.length >=
                                                sortBumperPrefixForPart.length
                                            ) {
                                                return prefix;
                                            }

                                            return sortBumperPrefixForPart;
                                        }, "") + suggestion.label;
                            }

                            // In `type:` value context, include all segments in filter text
                            // (e.g. `pub` matches both `Publish` and `pubsub`).
                            if (isTypeValueContext) {
                                const segments = getLabelSegments(
                                    suggestion.label,
                                );
                                suggestion.filterText = [
                                    suggestion.label.toLowerCase(),
                                    ...segments,
                                    segments.join(""),
                                ].join(" ");
                            } else {
                                suggestion.filterText =
                                    (suggestion.label.includes(wordAtPosition)
                                        ? wordAtPosition + " "
                                        : "") + suggestion.label.toLowerCase();
                            }
                        }

                        if (
                            suggestion.sortText === undefined &&
                            suggestion.label.includes(wordAtPosition)
                        ) {
                            suggestion.sortText =
                                sortBumperText + suggestion.label;
                        }
                    }

                    suggestion.sortText = suggestion.sortText?.toLowerCase();
                    return suggestion;
                })
                // ---- Keep `type:` filtering scoped to plugin type suggestions ----
                .filter((suggestion) => {
                    if (!isTypeValueContext) return true;
                    if (!typed) return true;

                    // Only apply this stricter filtering logic to dotted plugin classes.
                    if (!suggestion.label.includes(".")) return true;

                    return matchesTypeInput(suggestion.label, typed);
                })
                // ---- Ensure Monaco matches against any segment in `type:` context ----
                .map((suggestion) => {
                    if (isTypeValueContext && suggestion.label.includes(".")) {
                        const segments = getLabelSegments(suggestion.label);
                        suggestion.filterText = [
                            suggestion.label.toLowerCase(),
                            ...segments,
                            segments.join(""),
                        ].join(" ");
                    }
                    return suggestion;
                });

            return {
                ...defaultCompletion,
                suggestions,
            };
        };
    }

    configureAutoCompletion(
        _: ReturnType<typeof useI18n>["t"],
        ___: monaco.editor.ICodeEditor | undefined,
    ) {
        const autoCompletionProviders: IDisposable[] = [];
        const yamlAutoCompletion = this._yamlAutoCompletion;

        // Values autocompletion
        autoCompletionProviders.push(
            monaco.languages.registerCompletionItemProvider("yaml", {
                triggerCharacters: [":"],
                async provideCompletionItems(model, position) {
                    const source = model.getValue();
                    const cursorPosition = model.getOffsetAt(position);
                    const parsed = YamlUtils.parse(source, false);

                    const currentWord = model.findPreviousMatch(
                        RegexProvider.beforeSeparator(),
                        position,
                        true,
                        false,
                        null,
                        true,
                    );
                    const elementUnderCursor = YamlUtils.localizeElementAtIndex(
                        source,
                        cursorPosition,
                    );
                    // No key under cursor means we cannot infer contextual value completions.
                    if (elementUnderCursor?.key === undefined) {
                        return NO_SUGGESTIONS;
                    }

                    const parentStartLine = model.getPositionAt(
                        elementUnderCursor.range![0],
                    ).lineNumber;
                    const autoCompletions =
                        await yamlAutoCompletion.valueAutoCompletion(
                            source,
                            parsed,
                            elementUnderCursor,
                        );
                    return {
                        suggestions: autoCompletions.map((autoCompletion) => {
                            const [label, isKey] = autoCompletion.split(
                                ":",
                            ) as [string, string | undefined];
                            let insertText = label;
                            const endColumn = endOfWordColumn(position, model);

                            // If completion is a value, insert a leading space when cursor is after `:`.
                            if (isKey === undefined) {
                                if (source.charAt(cursorPosition - 1) === ":") {
                                    insertText = ` ${label}`;
                                }
                            } else {
                                // If completion is a key, keep indentation and `key: ` formatting.
                                if (parentStartLine === position.lineNumber) {
                                    insertText = `\n  ${label}: `;
                                } else {
                                    insertText =
                                        model
                                            .getLineContent(position.lineNumber)
                                            .charAt(endColumn - 1) === ":"
                                            ? label
                                            : `${label}: `;
                                }
                            }

                            return {
                                kind:
                                    isKey === undefined
                                        ? monaco.languages.CompletionItemKind
                                            .Value
                                        : monaco.languages.CompletionItemKind
                                            .Property,
                                label,
                                insertText: insertText,
                                range: {
                                    startLineNumber: position.lineNumber,
                                    endLineNumber: position.lineNumber,
                                    startColumn:
                                        position.column -
                                        (currentWord?.matches?.[0]?.length ??
                                            0),
                                    endColumn: endColumn,
                                },
                            };
                        }),
                    };
                },
            }),
        );

        autoCompletionProviders.push(
            monaco.languages.registerInlineCompletionsProvider("yaml", {
                provideInlineCompletions: async (model: any, position: any) => {
                    // Only suggest inline required properties in flow/testsuite editors.
                    const isFlowModel =
                        model.uri.path.includes("flow-") ||
                        model.uri.path.includes("testsuites-");
                    if (!isFlowModel) return {items: []};

                    const lineContent = model.getLineContent(
                        position.lineNumber,
                    );
                    const linePrefix = lineContent.slice(
                        0,
                        Math.max(position.column - 1, 0),
                    );
                    // Only trigger when the current line is empty up to the cursor.
                    if (!/^\s*$/.test(linePrefix)) return {items: []};

                    const previousLine =
                        position.lineNumber > 1
                            ? model.getLineContent(position.lineNumber - 1)
                            : "";

                    // Extract type value from previous line
                    const previous = previousLine.match(
                        /^\s*(?:-\s*)?type\s*:\s*(.+?)\s*$/,
                    );
                    if (!previous) return {items: []};

                    // Remove optional quotes around class names: `type: "..."`.
                    const cls = previous[1].replace(/^["']|["']$/g, "");
                    if (!cls) return {items: []};

                    const pluginsStore = usePluginsStore();

                    if (
                        typeof pluginsStore.updateDocumentation === "function"
                    ) {
                        await pluginsStore.updateDocumentation({cls});
                    }

                    const allProperties =
                        pluginsStore.editorPlugin?.schema?.properties
                            ?.properties ?? {};
                    const requiredProperties = Object.keys(
                        allProperties,
                    ).filter((p) => allProperties[p]?.$required === true);
                    // Nothing required means no inline snippet to propose.
                    if (!requiredProperties.length) return {items: []};

                    const missingRequiredProperties =
                        filterMissingRequiredTaskProperties({
                            source: model.getValue(),
                            cursorIndex: model.getOffsetAt(position),
                            requiredProperties,
                        });
                    if (!missingRequiredProperties.length) return {items: []};

                    const indent = lineContent.match(/^\s*/)?.[0] ?? "";
                    // Build a multi-line snippet containing all required keys.
                    const snippet = missingRequiredProperties
                        .map((k, i) => `${i > 0 ? indent : ""}${k}: `)
                        .join("\n");

                    const column =
                        indent.length +
                        missingRequiredProperties[0].length +
                        2 +
                        1;

                    return {
                        items: [
                            {
                                insertText: snippet,
                                insertTextRules:
                                    monaco.languages
                                        .CompletionItemInsertTextRule
                                        .InsertAsSnippet,
                                range: new monaco.Range(
                                    position.lineNumber,
                                    position.column,
                                    position.lineNumber,
                                    position.column,
                                ),
                                command: {
                                    id: "moveCursor",
                                    arguments: [
                                        {
                                            lineNumber: position.lineNumber,
                                            column,
                                        },
                                    ],
                                },
                            },
                        ],
                        enableForwardStability: true,
                    };
                },
                handleItemDidShow() { },
                handlePartialAccept() { },
                freeInlineCompletions() { },
            } as any),
        );

        registerPebbleAutocompletion(
            autoCompletionProviders,
            yamlAutoCompletion,
            ["yaml", "plaintext"],
        );

        registerFunctionParametersAutoCompletion(
            autoCompletionProviders,
            yamlAutoCompletion,
            ["yaml", "plaintext"],
        );

        registerNestedValueAutoCompletion(
            autoCompletionProviders,
            yamlAutoCompletion,
            ["yaml", "plaintext"],
        );

        return autoCompletionProviders;
    }
}
