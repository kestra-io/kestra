import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import {editor, IPosition, IRange} from "monaco-editor/esm/vs/editor/editor.api";
import AbstractLanguageConfigurator from "../abstractLanguageConfigurator";
import {Store} from "vuex";
import {useI18n} from "vue-i18n";
import {FilterLanguage} from "./filterLanguage";
import {useValues} from "../../../../components/filter/composables/useValues";
import {Comparators, Completion, PICK_DATE_VALUE} from "./filterCompletion";
import loadFilterLanguages from "override/services/filterLanguagesProvider";
import IWordAtPosition = editor.IWordAtPosition;

const legacyFilterLanguage = "legacy-filter";
export const languages = ["filter", legacyFilterLanguage];
export const COMPARATOR_CHARS = [...new Set(Object.values(Comparators).flatMap(c => c.split("")))];

let filterLanguages: FilterLanguage[];

export default class FilterLanguageConfigurator extends AbstractLanguageConfigurator {
    private _filterLanguage: FilterLanguage | undefined;
    private readonly _domain: string | undefined;
    private keyCompletions: Completion[] | undefined;
    private keyCompletionsRegex: RegExp | undefined;

    constructor(language: typeof languages[number], domain: string | undefined) {
        super(language);

        this._domain = domain;
    }

    isLegacy() {
        return this.language === legacyFilterLanguage;
    }

    async configure(store: Store<Record<string, any>>, t: ReturnType<typeof useI18n>["t"], editorInstance: editor.ICodeEditor | undefined): Promise<monaco.IDisposable[]> {
        filterLanguages = await loadFilterLanguages();

        this._filterLanguage = filterLanguages.find(filterLanguage => filterLanguage.domain === this._domain);
        this.keyCompletions = await this._filterLanguage?.keyCompletion();
        this.keyCompletionsRegex = new RegExp(this.keyCompletions === undefined
            ? ""
            : (
                "(?:" + this.keyCompletions
                    ?.map(k => k.label
                        .replaceAll(".", "\\.")
                        .replaceAll(/\{[^}]*}/g, "(?:(?:\"[^,\"" + COMPARATOR_CHARS.join("") + "]*\")|(?:[^\\s,\"" + COMPARATOR_CHARS.join("") + "]*))"))
                    ?.join(")|(?:") + ")"
            ));
        return super.configure(store, t, editorInstance);
    }

    async configureLanguage(_: Store<Record<string, any>>): Promise<void> {
        if (this._filterLanguage && monaco.languages.getLanguages().find(l => l.id === this.language) === undefined) {
            monaco.languages.register({id: this.language});
            monaco.languages.setMonarchTokensProvider(this.language, {
                operators: Object.values(Comparators),
                symbols: new RegExp("[" + COMPARATOR_CHARS.join("") + "]+"),
                includeLF: true,
                tokenizer: {
                    root: [
                        [/[\w\\.]+/, {
                            cases: {
                                [this.keyCompletionsRegex!.source]: {token: "variable.name", next: "@comparator"},
                                "@default": "invalid"
                            }
                        }]
                    ],
                    comparator: [
                        [/@symbols/, {
                            cases: {
                                "@operators": {token: "operators", next: "@value"}
                            }
                        }]
                    ],
                    value: [
                        [/"[^"]+(?![^"]*")/, "invalid"],
                        [new RegExp("\"[^,\"" + COMPARATOR_CHARS.join("") + "]*\""), {
                            token: "variable.value",
                            next: "@separator"
                        }],
                        [new RegExp("[^\\s,\"" + COMPARATOR_CHARS.join("") + "]*"), {
                            token: "variable.value",
                            next: "@separator"
                        }]
                    ],
                    separator: [
                        [/\s+/, {token: "space", next: "@popall"}],
                        [",", {token: "comma", next: "@value"}]
                    ]
                }
            });
        }
    }

    configureAutoCompletion(t: ReturnType<typeof useI18n>["t"], store: Store<Record<string, any>>, __: editor.ICodeEditor | undefined) {
        const filterLanguage = this._filterLanguage;
        if (filterLanguage === undefined) {
            return [];
        }

        const {VALUES: hardcodedValues} = useValues(this._domain, t);
        const SEPARATOR_CHARS = [" ", "\n"];
        const TO_SUGGESTIONS = (position: IPosition, wordAtPosition: IWordAtPosition & Partial<IRange>, completions: Completion[]) => {
            const startColumn = wordAtPosition?.startColumn ?? position.column;
            const endColumn = wordAtPosition?.endColumn ?? position.column;
            const range = {
                startLineNumber: wordAtPosition?.startLineNumber ?? position.lineNumber,
                endLineNumber: wordAtPosition?.endLineNumber ?? position.lineNumber,
                startColumn: wordAtPosition.word.startsWith("\"") ? startColumn + 1 : startColumn,
                endColumn: endColumn
            };

            return {
                suggestions: completions.map(({label, value}, index) => {
                    let insertText = value;
                    if (wordAtPosition.word.startsWith("\"") && value.endsWith(" ")) {
                        insertText = value.substring(0, value.length - 1) + "\"" + " ";
                    }

                    return ({
                        kind: monaco.languages.CompletionItemKind.Property,
                        label: label,
                        insertText,
                        sortText: "1a".repeat(index + 1),
                        insertTextRules: value.includes("${1:") ? monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet : undefined,
                        range,
                        command: {
                            id: "editor.action.triggerSuggest",
                            title: "Suggest"
                        }
                    });
                })
            };
        };
        const KEY_COMPLETIONS: Promise<Completion[]> = filterLanguage.keyCompletion();
        const filterLanguageConfiguratorInstance = this;
        return [
            monaco.languages.registerCompletionItemProvider({
                language: this.language, pattern: {
                    base: "/",
                    pattern: `${this._domain === undefined ? "" : (this._domain + "-")}*.${this.language}`
                }
            }, {
                triggerCharacters: [" ", "\n", ","],
                async provideCompletionItems(model, position) {
                    const wordAfterPosition = model.findNextMatch(
                        "([^,\\s" + COMPARATOR_CHARS.join("") + "]*)",
                        position,
                        true,
                        false,
                        null,
                        true
                    );
                    const wordAtPositionMatch = model.findPreviousMatch(
                        "([^,\\s" + COMPARATOR_CHARS.join("") + "]*)$",
                        position.with(undefined, wordAfterPosition?.range.endColumn),
                        true,
                        false,
                        null,
                        true
                    );

                    let wordAtPosition: IWordAtPosition & Partial<IRange> = {
                        word: wordAtPositionMatch?.matches?.[1] ?? "",
                        startColumn: wordAtPositionMatch?.range?.startColumn ?? position.column,
                        endColumn: wordAtPositionMatch?.range?.endColumn ?? position.column
                    };

                    const modelValue = model.getValue();
                    const offset = model.getOffsetAt(position);
                    const previousChar = modelValue.charAt(offset - 1);
                    const inQuotedString = modelValue.substring(0, offset).split("\"").length % 2 === 0;

                    if (inQuotedString) {
                        const previousQuote = model.findPreviousMatch("\"", position, false, false, null, true)?.range;
                        wordAtPosition = {
                            ...wordAtPosition,
                            startLineNumber: previousQuote?.startLineNumber ?? wordAtPosition.startLineNumber,
                            startColumn: previousQuote?.startColumn ?? wordAtPosition.startColumn
                        };
                    }

                    if (offset === 0 || (SEPARATOR_CHARS.includes(previousChar) && !inQuotedString)) {
                        const comparatorsAfterCurrentWord = model.findNextMatch(
                            "([" + COMPARATOR_CHARS.join("") + "]+)",
                            position.with(undefined, wordAtPosition.endColumn),
                            true,
                            false,
                            null,
                            true
                        );
                        return TO_SUGGESTIONS(
                            position,
                            {
                                ...wordAtPosition,
                                endColumn: wordAtPosition.endColumn + (comparatorsAfterCurrentWord?.matches?.[1]?.length ?? 0)
                            },
                            await KEY_COMPLETIONS
                        );
                    }

                    if (wordAtPosition.word.match(filterLanguageConfiguratorInstance.keyCompletionsRegex!) && position.column === wordAtPosition.endColumn) {
                        if (previousChar === ".") {
                            return TO_SUGGESTIONS(
                                position,
                                {word: "", startColumn: wordAtPosition.endColumn, endColumn: wordAtPosition.endColumn},
                                []
                            );
                        }

                        const comparatorCompletions = await filterLanguage.comparatorCompletion(wordAtPosition.word);
                        return TO_SUGGESTIONS(
                            position,
                            {word: "", startColumn: wordAtPosition.endColumn, endColumn: wordAtPosition.endColumn},
                            filterLanguageConfiguratorInstance.isLegacy() ? [comparatorCompletions[0]] : comparatorCompletions
                        );
                    }

                    const previousColumn = wordAtPosition.startColumn - 1;
                    const beforeWordPosition = previousColumn > 0
                        ? position.with(undefined, previousColumn)
                        : undefined;
                    const charBeforeCurrentWord = beforeWordPosition === undefined ? "" : modelValue.charAt(model.getOffsetAt(beforeWordPosition));

                    if (
                        COMPARATOR_CHARS.includes(previousChar)
                        || (
                            charBeforeCurrentWord !== undefined && (
                                COMPARATOR_CHARS.includes(charBeforeCurrentWord)
                                || charBeforeCurrentWord === ","
                            )
                        ) || inQuotedString
                    ) {
                        const currentFilterMatch = model.findPreviousMatch(
                            "(" + filterLanguageConfiguratorInstance.keyCompletionsRegex?.source + ")" +
                            "([" + COMPARATOR_CHARS.join("") + "]+)" +
                            "((?:\"[^\"]*\"?)|(?:[^\\s\"]*))$",
                            beforeWordPosition ?? position.with(undefined, wordAtPosition.startColumn),
                            true,
                            false,
                            null,
                            true
                        );

                        if (currentFilterMatch !== null) {
                            const [, key, comparator, commaSeparatedValues] = currentFilterMatch?.matches ?? [];

                            if (key !== undefined) {
                                const valueCompletions = await filterLanguage.valueCompletion(
                                    store,
                                    hardcodedValues,
                                    key
                                );
                                if (Array.isArray(valueCompletions)) {
                                    const filledValues = commaSeparatedValues === undefined ? [] : commaSeparatedValues.split(",");
                                    const remainingCompletions = valueCompletions
                                        .filter(completion => !filledValues.includes(completion.value) && !filledValues.includes("\"" + completion.value + "\""));
                                    const completions = remainingCompletions
                                        .map(({label, value}) => new Completion(
                                            label,
                                            value +
                                            (
                                                ([Comparators.EQUALS, Comparators.NOT_EQUALS] as string[]).includes(comparator)
                                                && remainingCompletions.length > 1
                                                && filterLanguage.multipleValuesAllowed(key)
                                                    ? ","
                                                    : value.includes("${1") ? "" : " "
                                            ))
                                        );
                                    return TO_SUGGESTIONS(position, wordAtPosition, completions);
                                } else if (valueCompletions === PICK_DATE_VALUE) {
                                    return TO_SUGGESTIONS(position, wordAtPosition, [new Completion("_DATE_PICKER_", "_DATE_PICKER_")]);
                                }
                            }
                        }
                    } else {
                        return TO_SUGGESTIONS(position, wordAtPosition, await KEY_COMPLETIONS);
                    }

                    return TO_SUGGESTIONS(position, wordAtPosition, await KEY_COMPLETIONS);
                }
            })
        ];
    }
}