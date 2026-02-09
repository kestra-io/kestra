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
    registerPebbleAutocompletion
} from "./pebbleLanguageConfigurator";
import {usePluginsStore} from "../../../stores/plugins";
import {useBlueprintsStore} from "../../../stores/blueprints";
import {languages} from "monaco-editor/esm/vs/editor/editor.api";
import CompletionItem = languages.CompletionItem;


export class YamlLanguageConfigurator extends AbstractLanguageConfigurator {
    private readonly _yamlAutoCompletion: YamlAutoCompletion;

    constructor(yamlAutoCompletion: YamlAutoCompletion) {
        super("yaml");
        this._yamlAutoCompletion = yamlAutoCompletion;
    }

    async configureLanguage(pluginsStore: ReturnType<typeof usePluginsStore>) {
        const validateYAML = computed(() => useBlueprintsStore().validateYAML);
        watch(validateYAML, (shouldValidate) => configureMonacoYaml(monaco, {validate: shouldValidate}));

        configureMonacoYaml(monaco, {
            enableSchemaRequest: true,
            hover: localStorage.getItem("hoverTextEditor") === "true",
            completion: true,
            validate: validateYAML.value ?? true,
            format: true,
            schemas: yamlSchemas()
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

            return {
                ...defaultCompletion,
                suggestions: (defaultCompletion.suggestions as (CompletionItem & {label: string})[]).map(suggestion => {
                    if (suggestion.label.endsWith("...") && suggestion.insertText.includes(suggestion.label.substring(0, suggestion.label.length - 3))) {
                        return {...suggestion, label: suggestion.insertText};
                    }
                    return suggestion;
                }).filter(suggestion => {
                    if (suggestion.label.includes(".")) {
                        return !pluginsStore.deprecatedTypes.includes(suggestion.label);
                    }

                    return true;
                }).map(suggestion => {
                    const wordAtPosition = model.getWordAtPosition(position)?.word?.toLowerCase();
                    if (wordAtPosition !== undefined) {
                        const sortBumperText = "a1".repeat(10);
                        if (suggestion.label.includes(".")) {
                            const dotSplit = suggestion.label.toLowerCase().split(/\.(?=\w)/);
                            if (dotSplit[dotSplit.length - 1].startsWith(wordAtPosition)) {
                                suggestion.sortText = sortBumperText.repeat(5) + suggestion.label;
                            } else if (dotSplit[dotSplit.length - 1].includes(wordAtPosition)) {
                                suggestion.sortText = sortBumperText.repeat(4) + suggestion.label;
                            } else {
                                suggestion.sortText = dotSplit.splice(dotSplit.length - 1, 1).reduceRight((prefix, part) => {
                                    let sortBumperPrefixForPart;
                                    if (part.startsWith(wordAtPosition)) {
                                        sortBumperPrefixForPart = sortBumperText.repeat(3)
                                    } else if (part.includes(wordAtPosition)) {
                                        sortBumperPrefixForPart = sortBumperText.repeat(2);
                                    }

                                    if (sortBumperPrefixForPart === undefined || prefix.length >= sortBumperPrefixForPart.length) {
                                        return prefix;
                                    }

                                    return sortBumperPrefixForPart;
                                }, "") + suggestion.label;
                            }

                            suggestion.filterText = (suggestion.label.includes(wordAtPosition) ? wordAtPosition + " " : "") + suggestion.label.toLowerCase();
                        }

                        if (suggestion.sortText === undefined && suggestion.label.includes(wordAtPosition)) {
                            suggestion.sortText = sortBumperText + suggestion.label;
                        }
                    }

                    suggestion.sortText = suggestion.sortText?.toLowerCase();

                    return suggestion;
                })
            };
        };
    }

    configureAutoCompletion(_: ReturnType<typeof useI18n>["t"], ___: monaco.editor.ICodeEditor | undefined) {
        const autoCompletionProviders: IDisposable[] = [];
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

        registerPebbleAutocompletion(autoCompletionProviders, yamlAutoCompletion, ["yaml", "plaintext"]);

        registerFunctionParametersAutoCompletion(autoCompletionProviders, yamlAutoCompletion, ["yaml", "plaintext"]);

        registerNestedValueAutoCompletion(autoCompletionProviders, yamlAutoCompletion, ["yaml", "plaintext"]);

        return autoCompletionProviders;
    }
}
