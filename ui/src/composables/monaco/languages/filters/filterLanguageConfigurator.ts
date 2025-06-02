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

const legacyFilterRegex = /.*((?<=.)-)?legacy-filter/;
export const languages = [/.*((?<=.)-)?filter/, legacyFilterRegex];
export const PER_COMPARATOR_REGEX = Object.entries(Comparators).reduce((acc, [key, value]) => {
    acc[key] = new RegExp(value.replaceAll(/[$.*^]/g, (match) => `\\${match}`));
    return acc;
}, {} as Record<string, RegExp>);
export const COMPARATORS_REGEX = "(?:" + Object.values(PER_COMPARATOR_REGEX)
    .sort((r1, r2) => r2.source.length - r1.source.length)
    .map(r => r.source).join("|") + ")";

let filterLanguages: FilterLanguage[];

export default class FilterLanguageConfigurator extends AbstractLanguageConfigurator {
    private _filterLanguage: FilterLanguage | undefined;
    private readonly _domain: string | undefined;
    private keyCompletions: Completion[] | undefined;
    private allKeyCompletionsRegex: RegExp | undefined;

    constructor(language: string, domain: string | undefined) {
        super(language);

        this._domain = domain;
    }

    isLegacy() {
        return legacyFilterRegex.test(this.language);
    }

    async configure(store: Store<Record<string, any>>, t: ReturnType<typeof useI18n>["t"], editorInstance: editor.ICodeEditor | undefined): Promise<monaco.IDisposable[]> {
        filterLanguages = await loadFilterLanguages();

        this._filterLanguage = filterLanguages.find(filterLanguage => filterLanguage.domain === this._domain);
        this.keyCompletions = await this._filterLanguage?.keyCompletion();
        this.allKeyCompletionsRegex = new RegExp(this.keyCompletions === undefined
            ? ""
            : (
                "(?:" + this.keyCompletions
                    ?.map(k => k.label
                        .replaceAll(".", "\\.")
                        .replaceAll(/\{[^}]*}/g, "(?:\"[^,\"]*\"|[^\\s,\"]*?(?=" + COMPARATORS_REGEX + "|\\s|$))"))
                    ?.join("|") + ")"
            ));

        return super.configure(store, t, editorInstance);
    }

    async configureLanguage(_: Store<Record<string, any>>): Promise<void> {
        const keyLabelToRegex = (keyLabel: string) => {
            return new RegExp(keyLabel
                .replaceAll(".", "\\.")
                .replaceAll(/\{[^}]*}/g, "(?:\"[^,\"]*\"|[^\\s,\"]*?(?=" + COMPARATORS_REGEX + "|\\s|$))"));
        };

        if (this._filterLanguage && monaco.languages.getLanguages().find(l => l.id === this.language) === undefined) {
            monaco.languages.register({id: this.language});

            const keysTokenizerCases = this.keyCompletions === undefined
                ? {}
                : this.keyCompletions!.reduce((acc, key) => {
                    acc[keyLabelToRegex(key.label).source] = {
                        token: "variable.name",
                        next: `@${key.label}-comparator`
                    };

                    return acc;
                }, {} as Record<string, monaco.languages.IMonarchLanguageAction>);

            const keysToValueTokenizer = this.keyCompletions === undefined
                ? {} as Record<string, monaco.languages.IMonarchLanguageRule[]>
                : await this.keyCompletions!.reduce(async (accPromise, completion) => {
                    return accPromise.then(async (acc) => ({
                        ...acc,
                        [`${completion.label}-comparator`]: [
                            [
                                new RegExp(
                                    (await this._filterLanguage!.comparatorCompletion(completion.value + (completion.value.endsWith(".") ? "placeholder" : "")))
                                        .map(comparator => PER_COMPARATOR_REGEX[comparator.label].source).join("|")
                                ),
                                {
                                    token: "operators",
                                    next: "@value"
                                }
                            ],
                            [
                                /[^\\s]*/,
                                {token: "@rematch", next: "@whitespace"}
                            ]
                        ]
                    } as Record<string, monaco.languages.IMonarchLanguageRule[]>));
                }, Promise.resolve({} as Record<string, monaco.languages.IMonarchLanguageRule[]>));

            monaco.languages.setMonarchTokensProvider(this.language, {
                operators: Object.values(Comparators),
                symbols: new RegExp(COMPARATORS_REGEX),
                defaultToken: "invalid",
                includeLF: true,
                tokenizer: {
                    root: [
                        [/[\w\\.]+/, {
                            cases: {
                                ...keysTokenizerCases,
                                "@default": {token: "@rematch", next: "@rawText"}
                            }
                        }],
                        [/[^\w\\.]/, {token: "invalid", next: "@whitespace"}]
                    ],
                    rawText: [
                        [/\S+/, {
                            cases: {
                                [`(?:(?!${COMPARATORS_REGEX})\\S(?!${COMPARATORS_REGEX}))*`]: {
                                    token: "variable.value",
                                    next: "@whitespace"
                                },
                                "@default": {token: "invalid", next: "@whitespace"}
                            }
                        }]
                    ],
                    value: [
                        [/"[^"]+(?![^"]*")/, "invalid"],
                        [new RegExp("\"[^\\n,\"]*\""), {
                            token: "variable.value",
                            next: "@separator"
                        }],
                        [new RegExp("[^\\s,\"]*"), {
                            token: "variable.value",
                            next: "@separator"
                        }]
                    ],
                    whitespace: [
                        [/\s+/, {token: "space", next: "@popall"}],
                        [/\S+/, {token: "invalid"}]
                    ],
                    separator: [
                        {include: "@whitespace"},
                        [",", {token: "comma", next: "@value"}],
                        [/\S+/, {token: "invalid"}]
                    ],
                    ...keysToValueTokenizer
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
                    pattern: `*.${this.language}`
                }
            }, {
                triggerCharacters: [" ", "\n", ","],
                async provideCompletionItems(model, position) {
                    const wordAfterPosition = model.findNextMatch(
                        `([^,\\s]*)(?:${COMPARATORS_REGEX}[^,\\s]*)?`,
                        position,
                        true,
                        false,
                        null,
                        true
                    );
                    const wordAtPositionMatch = model.findPreviousMatch(
                        `(?:^|\\s)(?:[^,\\s]*${COMPARATORS_REGEX})?([^,\\s]*)$`,
                        position.with(undefined, wordAfterPosition?.range.endColumn),
                        true,
                        false,
                        null,
                        true
                    );

                    const endColumn = wordAtPositionMatch?.range?.endColumn ?? position.column;
                    const wordMatch = wordAtPositionMatch?.matches?.[1] ?? "";
                    let wordAtPosition: IWordAtPosition & Partial<IRange> = {
                        word: wordMatch,
                        startColumn: endColumn - wordMatch.length,
                        endColumn: endColumn
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
                            "(" + COMPARATORS_REGEX + ")",
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

                    if (wordAtPosition.word.match(new RegExp("^" + filterLanguageConfiguratorInstance.allKeyCompletionsRegex!.source + "$")) && position.column === wordAtPosition.endColumn) {
                        if (previousChar === ".") {
                            return TO_SUGGESTIONS(
                                position,
                                {
                                    word: "",
                                    startColumn: wordAtPosition.endColumn,
                                    endColumn: wordAtPosition.endColumn
                                },
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

                    const previousColumn = wordAtPosition.startColumn;
                    const beforeWordPosition = previousColumn > 0
                        ? position.with(undefined, previousColumn)
                        : undefined;

                    const currentFilterMatch = model.findPreviousMatch(
                        "(" + filterLanguageConfiguratorInstance.allKeyCompletionsRegex?.source + ")" +
                        "(" + COMPARATORS_REGEX + ")" +
                        "(\"[^\"]*\"?|[^\\s\"]*)$",
                        beforeWordPosition ?? position.with(undefined, wordAtPosition.startColumn),
                        true,
                        false,
                        null,
                        true
                    );

                    if (currentFilterMatch === null) {
                        return TO_SUGGESTIONS(position, wordAtPosition, await KEY_COMPLETIONS);
                    } else {
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
                }
            })];
    }
}