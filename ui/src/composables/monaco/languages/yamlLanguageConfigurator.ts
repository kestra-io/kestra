import {configureMonacoYaml} from "monaco-yaml";
import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import {languages} from "monaco-editor/esm/vs/editor/editor.api";
import {yamlSchemas} from "override/utils/yamlSchemas";
import {StandaloneServices} from "monaco-editor/esm/vs/editor/standalone/browser/standaloneServices";
import {ILanguageFeaturesService} from "monaco-editor/esm/vs/editor/common/services/languageFeatures";
import AbstractLanguageConfigurator from "./abstractLanguageConfigurator";
import {QUOTE, YamlAutoCompletion} from "../../../services/autoCompletionProvider.ts";
import RegexProvider from "../../../utils/regex";
import {YamlUtils} from "@kestra-io/ui-libs";
import {Store} from "vuex";
import {useI18n} from "vue-i18n";
import IPosition = monaco.IPosition;
import IDisposable = monaco.IDisposable;
import IModel = monaco.editor.IModel;
import ProviderResult = monaco.languages.ProviderResult;
import CompletionList = monaco.languages.CompletionList;
import CompletionItem = languages.CompletionItem;


export class YamlLanguageConfigurator extends AbstractLanguageConfigurator {
    private readonly _yamlAutoCompletion: YamlAutoCompletion;

    constructor(yamlAutoCompletion: YamlAutoCompletion) {
        super("yaml");
        this._yamlAutoCompletion = yamlAutoCompletion;
    }

    async configureLanguage(store: Store<Record<string, any>>) {
        configureMonacoYaml(monaco, {
            enableSchemaRequest: true,
            hover: true,
            completion: true,
            validate: true,
            format: true,
            schemas: yamlSchemas(store)
        });

        const yamlCompletion = (StandaloneServices.get(ILanguageFeaturesService).completionProvider._entries as {
            selector: string,
            provider: {
                provideCompletionItems: (model: IModel, position: IPosition) => ProviderResult<CompletionList>
            }
        }[]).find(completion => completion.selector === "yaml");

        if (yamlCompletion === undefined) {
            return;
        }

        const initialCompletion = yamlCompletion.provider.provideCompletionItems;
        yamlCompletion.provider.provideCompletionItems = async function (model: IModel, position: IPosition) {
            const defaultCompletion = await initialCompletion(model, position);
            if (!defaultCompletion) {
                return defaultCompletion;
            }

            (defaultCompletion.suggestions as {
                label: string,
                filterText: string,
                insertText: string
            }[]).forEach(suggestion => {
                if (suggestion.label.endsWith("...") && suggestion.insertText.includes(suggestion.label.substring(0, suggestion.label.length - 3))) {
                    suggestion.label = suggestion.insertText;
                }

                if (suggestion.label.includes(".")) {
                    const dotSplit = suggestion.label.split(/\.(?=\w)/);
                    const taskName = dotSplit.pop();
                    suggestion.filterText = [taskName, ...dotSplit, taskName].join(".");
                }
            });


            return defaultCompletion;
        };
    }

    configureAutoCompletion(_: ReturnType<typeof useI18n>["t"], __: Store<Record<string, any>>, ___: monaco.editor.ICodeEditor | undefined) {
        const NO_SUGGESTIONS = {suggestions: []};

        const autoCompletionProviders: IDisposable[] = [];

        const QUOTES = ["\"", "'"];
        const endOfWordColumn = (position: IPosition, model: IModel): number => {
            return position.column + (model.findNextMatch(
                RegexProvider.beforeSeparator(QUOTES),
                position,
                true,
                false,
                null,
                true
            )?.matches?.[0]?.length ?? 0);
        }

        const yamlAutoCompletion = this._yamlAutoCompletion;
        // Values autocompletion
        autoCompletionProviders.push(monaco.languages.registerCompletionItemProvider("yaml", {
            triggerCharacters: [":"],
            async provideCompletionItems(model, position) {
                const source = model.getValue();
                const cursorPosition = model.getOffsetAt(position);
                const parsed = YamlUtils.parse(source, false);

                const currentWord = model.findPreviousMatch(RegexProvider.beforeSeparator(), position, true, false, null, true);
                const elementUnderCursor = YamlUtils.localizeElementAtIndex(source, cursorPosition);
                if (elementUnderCursor?.key === undefined) {
                    return NO_SUGGESTIONS;
                }

                const parentStartLine = model.getPositionAt(elementUnderCursor.range![0]).lineNumber;
                const autoCompletions = await yamlAutoCompletion.valueAutoCompletion(source, parsed, elementUnderCursor);
                return {
                    suggestions: autoCompletions.map(autoCompletion => {
                        const [label, isKey] = autoCompletion.split(":") as [string, string | undefined];
                        let insertText = label;
                        const endColumn = endOfWordColumn(position, model);
                        if (isKey === undefined) {
                            if (source.charAt(cursorPosition - 1) === ":") {
                                insertText = ` ${label}`;
                            }
                        } else {
                            if (parentStartLine === position.lineNumber) {
                                insertText = `\n  ${label}: `;
                            } else {
                                insertText = model.getLineContent(position.lineNumber).charAt(endColumn - 1) === ":" ? label : `${label}: `;
                            }
                        }
                        return ({
                            kind: isKey === undefined ? monaco.languages.CompletionItemKind.Value : monaco.languages.CompletionItemKind.Property,
                            label,
                            insertText: insertText,
                            range: {
                                startLineNumber: position.lineNumber,
                                endLineNumber: position.lineNumber,
                                startColumn: position.column - (currentWord?.matches?.[0]?.length ?? 0),
                                endColumn: endColumn
                            }
                        });
                    })
                };
            }
        }));

        const propertySuggestion = (value: string, position: {
            lineNumber: number,
            startColumn: number,
            endColumn: number
        }, kind?: monaco.languages.CompletionItemKind): CompletionItem => {
            let label = value.split("(")[0];
            if (label.startsWith(QUOTE) && label.endsWith(QUOTE)) {
                label = label.substring(1, label.length - 1);
            }

            return ({
                kind: kind ?? (value.includes("(") ? monaco.languages.CompletionItemKind.Function : monaco.languages.CompletionItemKind.Property),
                label: label,
                insertText: value,
                insertTextRules: value.includes("${1:") ? monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet : undefined,
                sortText: value.includes("(") ? "b" + value : "a" + value,
                range: {
                    startLineNumber: position.lineNumber,
                    endLineNumber: position.lineNumber,
                    startColumn: position.startColumn,
                    endColumn: position.endColumn
                }
            });
        };

        // Pebble autocompletion
        autoCompletionProviders.push(monaco.languages.registerCompletionItemProvider(["yaml", "plaintext"], {
            triggerCharacters: ["{"],
            async provideCompletionItems(model, position) {
                // Not a subfield access
                const rootPebbleVariableMatcher = model.findPreviousMatch(RegexProvider.capturePebbleVarRoot + "$", position, true, false, null, true);
                if (rootPebbleVariableMatcher === null || rootPebbleVariableMatcher.matches === null) {
                    return NO_SUGGESTIONS;
                }

                const startOfWordColumn = position.column - rootPebbleVariableMatcher.matches[1].length;
                return {
                    suggestions: (await (yamlAutoCompletion.rootFieldAutoCompletion()))
                        .map(s => propertySuggestion(s, {
                            lineNumber: position.lineNumber,
                            startColumn: startOfWordColumn,
                            endColumn: endOfWordColumn(position, model)
                        }))
                };
            }
        }));

        // Function parameters autocompletion
        autoCompletionProviders.push(monaco.languages.registerCompletionItemProvider(["yaml", "plaintext"], {
            triggerCharacters: ["("],
            async provideCompletionItems(model, position) {
                const source = model.getValue();
                const parsed = YamlUtils.parse(source, false);

                const functionMatcher = model.findPreviousMatch(RegexProvider.capturePebbleFunction + "$", position, true, false, null, true);
                if (functionMatcher === null || functionMatcher.matches === null) {
                    return NO_SUGGESTIONS;
                }

                const wordStartOffset = functionMatcher.matches?.[3]?.length
                    ?? (model.findPreviousMatch(RegexProvider.beforeSeparator(QUOTES) + "$", position, true, false, null, true)?.matches?.[0]?.length)
                    ?? 0;
                const startOfWordColumn = position.column - wordStartOffset;
                return {
                    suggestions: (await yamlAutoCompletion.functionAutoCompletion(
                            parsed,
                            functionMatcher.matches[1],
                            Object.fromEntries(functionMatcher.matches?.[2]?.split(/ *, */)?.map(arg => arg.split(/ *= */)) ?? []))
                    ).map(s => {
                        const endColumn = endOfWordColumn(position, model);
                        const suggestion = propertySuggestion(s, {
                            lineNumber: position.lineNumber,
                            startColumn: startOfWordColumn,
                            endColumn: endColumn
                        }, monaco.languages.CompletionItemKind.Value);

                        // If the inserted value is a string (surrounded by quotes), we remove them if there is already one
                        if (suggestion.insertText.startsWith(QUOTE) && suggestion.insertText.endsWith(QUOTE)) {
                            const lineContent = model.getLineContent(position.lineNumber);
                            suggestion.insertText = suggestion.insertText.substring(
                                QUOTES.includes(lineContent.charAt(startOfWordColumn - 2)) ? 1 : 0,
                                suggestion.insertText.length - (QUOTES.includes(lineContent.charAt(endColumn - 1)) ? 1 : 0)
                            );
                        }

                        return suggestion;
                    })
                };
            }
        }))

        // Nested value autocompletion
        autoCompletionProviders.push(monaco.languages.registerCompletionItemProvider(["yaml", "plaintext"], {
            triggerCharacters: ["."],
            async provideCompletionItems(model, position) {
                const source = model.getValue();
                const parsed = YamlUtils.parse(source, false);

                const parentFieldMatcher = model.findPreviousMatch(RegexProvider.capturePebbleVarParent + "$", position, true, false, null, true);
                if (parentFieldMatcher === null || parentFieldMatcher.matches === null) {
                    return NO_SUGGESTIONS;
                }

                const startOfWordColumn = position.column - parentFieldMatcher.matches[2].length;
                return {
                    suggestions: (await yamlAutoCompletion.nestedFieldAutoCompletion(source, parsed, parentFieldMatcher.matches[1]))
                        .map(s => propertySuggestion(s, {
                            lineNumber: position.lineNumber,
                            startColumn: startOfWordColumn,
                            endColumn: endOfWordColumn(position, model)
                        }))
                };
            }
        }));

        return autoCompletionProviders;
    }
}