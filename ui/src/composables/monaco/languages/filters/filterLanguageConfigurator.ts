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
import {usePluginsStore} from "../../../../stores/plugins.ts";

const legacyFilterRegex = /.*((?<=.)-)?legacy-filter/;
export const languages = [/.*((?<=.)-)?filter/, legacyFilterRegex];
const PER_COMPARATOR_REGEX = Object.entries(Comparators).reduce((acc, [key, value]) => {
    acc[key] = new RegExp(value.replaceAll(/[$.*^]/g, (match) => `\\${match}`));
    return acc;
}, {} as Record<string, RegExp>);
export const COMPARATORS_REGEX = "(?:" + Object.values(PER_COMPARATOR_REGEX)
    .sort((r1, r2) => r2.source.length - r1.source.length)
    .map(r => r.source).join("|") + ")";
const COMPARATORS_CHARS_REGEX = "[" + [...new Set(Object.values(Comparators).join("").replaceAll("-", "\\-").split(""))].join("") + "]";

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

    async configure(store: Store<Record<string, any>>, pluginsStore: ReturnType<typeof usePluginsStore>, t: ReturnType<typeof useI18n>["t"], editorInstance: editor.ICodeEditor | undefined): Promise<monaco.IDisposable[]> {
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

        return super.configure(store, pluginsStore, t, editorInstance);
    }

    async configureLanguage(): Promise<void> {
        const keyLabelToRegex = (keyLabel: string) => {
            return new RegExp(keyLabel
                .replaceAll(".", "\\.")
                .replaceAll(/\{[^}]*}/g, "(?:\"[^\"]*\"|[^\\s,\"]*?(?=" + COMPARATORS_REGEX + "|\\s|$))"));
        };

        if (this._filterLanguage && monaco.languages.getLanguages().find(l => l.id === this.language) === undefined) {
            monaco.languages.register({id: this.language});

            const keysTokenizerCases = this.keyCompletions === undefined
                ? {}
                : this.keyCompletions!.reduce((acc, key) => {
                    acc[keyLabelToRegex(key.label).source] = {
                        token: "variable.name",
                        next: `@${FilterLanguage.withNestedKeyPlaceholder(key.label)}-comparator`
                    };

                    return acc;
                }, {} as Record<string, monaco.languages.IMonarchLanguageAction>);

            const keysToValueTokenizer = this.keyCompletions === undefined
                ? {} as Record<string, monaco.languages.IMonarchLanguageRule[]>
                : Object.entries(this._filterLanguage?.comparatorsPerKey()).reduce((acc, [key, comparatorKeys]) => ({
                        ...acc,
                        [`${key}-comparator`]: [
                            [
                                new RegExp(
                                    comparatorKeys.map(comparator => PER_COMPARATOR_REGEX[comparator].source).join("|")
                                ),
                                {token: "operators", next: "@value"}
                            ],
                            [
                                /\S*/,
                                {token: "@rematch", next: "@whitespace"}
                            ]
                        ]
                    } as Record<string, monaco.languages.IMonarchLanguageRule[]>),
                    {} as Record<string, monaco.languages.IMonarchLanguageRule[]>
                );

            monaco.languages.setMonarchTokensProvider(this.language, {
                defaultToken: "invalid",
                includeLF: true,
                tokenizer: {
                    root: [
                        [/[\w.]*(?:"[^"]*")?[\w.]*/, {
                            cases: {
                                ...keysTokenizerCases,
                                "@default": {token: "@rematch", next: "@rawText"}
                            }
                        }],
                        [/[^\w."]/, {token: "invalid", next: "@whitespace"}]
                    ],
                    rawText: [
                        [/"[^"]*"|\S+/, {
                            cases: {
                                [`\\S*${COMPARATORS_REGEX}\\S*`]: {
                                    token: "invalid",
                                    next: "@whitespace"
                                },
                                "@default": {token: "variable.value", next: "@whitespace"}
                            }
                        }]
                    ],
                    value: [
                        [/"[^"]+(?![^"]*")/, "invalid"],
                        [new RegExp("\"[^\\n\"]*\""), {
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
                        [",", {token: "comma", next: "@value"}],
                        {include: "@whitespace"},
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
                    const modelValue = model.getValue();
                    const offset = model.getOffsetAt(position);
                    const inQuotedString = modelValue.substring(0, offset).split("\"").length % 2 === 0;

                    const wordAfterPositionMatcher = (partialComparatorMatch: boolean) => {
                        return model.findNextMatch(
                            (inQuotedString ? "[^\"]*\"?" : `(?:(?!${COMPARATORS_REGEX})[^,\\s])+?(?=${COMPARATORS_REGEX})${partialComparatorMatch ? `|${COMPARATORS_CHARS_REGEX}+` : ""}|[^,\\s]*`),
                            position,
                            true,
                            false,
                            null,
                            true
                        );
                    };

                    let wordAfterPositionMatch = wordAfterPositionMatcher(true);

                    const wordAtPositionMatcher = (position: IPosition) => {
                        return model.findPreviousMatch(
                            "(?:" + (inQuotedString
                                ? "(\"[^\"]*\"?)"
                                : `[^,\\s]*?(${COMPARATORS_REGEX})([^,\\s]*)|([^,\\s]*)`)
                            + ")$",
                            position,
                            true,
                            false,
                            null,
                            true
                        )!;
                    };

                    let wordAtPositionMatch = wordAtPositionMatcher(position.with(undefined, wordAfterPositionMatch?.range.endColumn));
                    const lastWordIsComparator = inQuotedString ? false : ((wordAtPositionMatch?.matches?.[2]?.length ?? 0) == 0 && (wordAtPositionMatch?.matches?.[3]?.length ?? 0) == 0);
                    if (lastWordIsComparator) {
                        if ((wordAfterPositionMatch?.matches?.[0]?.length ?? 0) > 0) {
                            wordAtPositionMatch = wordAtPositionMatcher(position.with(undefined, wordAtPositionMatch.range.endColumn - (wordAtPositionMatch.matches?.[1]?.length ?? 0)));
                        } else {
                            wordAtPositionMatch = wordAfterPositionMatch!;
                        }
                    } else {
                        wordAfterPositionMatch = wordAfterPositionMatcher(false);
                        wordAtPositionMatch = wordAtPositionMatcher(position.with(undefined, wordAfterPositionMatch?.range.endColumn));
                    }

                    const endColumn = wordAtPositionMatch?.range?.endColumn ?? position.column;
                    const wordMatch = wordAtPositionMatch?.matches?.[3] ?? (wordAtPositionMatch?.matches?.[2]?.length === 0 ? wordAtPositionMatch?.matches?.[1] : wordAtPositionMatch?.matches?.[2]) ?? "";
                    const wordAtPosition: IWordAtPosition & Partial<IRange> = {
                        word: wordMatch,
                        startColumn: endColumn - wordMatch.length,
                        endColumn: endColumn
                    };

                    const previousChar = modelValue.charAt(offset - 1);

                    const comparatorsAfterCurrentWord = model.findNextMatch(
                        "(" + COMPARATORS_REGEX + ")?[\\s\\S]*$",
                        position.column === wordAtPosition.startColumn ? position : position.with(undefined, wordAtPosition.endColumn),
                        true,
                        false,
                        null,
                        true
                    );

                    const usedKeys = [...modelValue.matchAll(new RegExp(`\\s?(\\S+?)${COMPARATORS_REGEX}`, "g"))]
                        .map(([_, key]) => FilterLanguage.withNestedKeyPlaceholder(key));
                    if (offset === 0
                        || (SEPARATOR_CHARS.includes(previousChar) && !inQuotedString)
                        || (!lastWordIsComparator && comparatorsAfterCurrentWord?.matches?.[1] !== undefined)) {
                        return TO_SUGGESTIONS(
                            position,
                            {
                                ...wordAtPosition,
                                endColumn: wordAtPosition.endColumn + (comparatorsAfterCurrentWord?.matches?.[1]?.length ?? 0)
                            },
                            await filterLanguage.keyCompletion(usedKeys)
                        );
                    }

                    if (wordAtPosition.word.match(new RegExp("^" + filterLanguageConfiguratorInstance.allKeyCompletionsRegex!.source + "$"))) {
                        if (previousChar === "." && !lastWordIsComparator) {
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
                            {
                                word: "",
                                startColumn: wordAtPosition.endColumn,
                                endColumn: lastWordIsComparator ? wordAfterPositionMatch!.range.endColumn : wordAtPosition.endColumn
                            },
                            filterLanguageConfiguratorInstance.isLegacy() ? [comparatorCompletions[0]] : comparatorCompletions
                        );
                    }

                    const currentFilterMatch = model.findPreviousMatch(
                        "(" + filterLanguageConfiguratorInstance.allKeyCompletionsRegex?.source + ")" +
                        "(" + COMPARATORS_REGEX + ")?" +
                        "(\"[^\"]*\"?|[^\\s\"]*)$",
                        position.with(undefined, wordAtPosition.endColumn),
                        true,
                        false,
                        null,
                        true
                    );

                    if (currentFilterMatch === null) {
                        return TO_SUGGESTIONS(position, wordAtPosition, await filterLanguage.keyCompletion(usedKeys));
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
