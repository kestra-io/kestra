import {vueRouter} from "storybook-vue3-router";
import {expect, userEvent, waitFor, within} from "storybook/test";
import KestraFilter from "../../../../src/components/filter/KestraFilter.vue";
import {type LocationQuery, stringifyQuery, useRoute} from "vue-router";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {FilterLanguage} from "../../../../src/composables/monaco/languages/filters/filterLanguage.ts";
import {
    Comparators,
    Completion,
    FilterKeyCompletions
} from "../../../../src/composables/monaco/languages/filters/filterCompletion.ts";
import loadFilterLanguages from "../../mocks/services/filterLanguagesProvider.mock.ts";
import DefaultFilterLanguage from "../../../../src/composables/monaco/languages/filters/impl/defaultFilterLanguage.ts";
import {isColoredAsError} from "../../utils/monacoUtils.ts";

const meta = {
    title: "Components/KestraFilter",
    component: KestraFilter
} satisfies Meta<typeof KestraFilter>;

export default meta;
type Story = StoryObj<typeof meta>;

declare global {
    interface Window {
        // Hack from Monaco editor to allow navigating through suggestions
        acceptSuggestion: () => void;
        nextSuggestion: () => void;
    }
}

let suggestionWidgetController: {
    accept: () => void,
    next: () => void
} = {
    accept() {
    },
    next() {
    }
};

function getDecorators(routeQuery?: LocationQuery) {
    return [
        () => {
            return {
                setup() {
                    const route = useRoute();

                    loadFilterLanguages.mockReturnValue(Promise.resolve([TestFilterLanguage.INSTANCE, DefaultFilterLanguage]));

                    return {route};
                },
                template: "<div><span>ROUTE QUERY: </span><span data-testid='routeQuery'>{{route.query}}</span><story /></div>",
            };
        },
        vueRouter(
            [
                {
                    path: "/",
                    name: "home",
                    component: {template: "<div>home</div>"},
                }
            ],
            {
                initialRoute: "/" + (routeQuery === undefined ? "" : `?${stringifyQuery(routeQuery!)}`),
            },
        ),
    ];
}

async function parseRouteQuery(canvas: any): Promise<LocationQuery> {
    return JSON.parse(canvas.getByTestId("routeQuery").textContent);
}

function waitForFilterToBeReady(user: ReturnType<typeof userEvent.setup>, canvas: ReturnType<typeof within>): Promise<void> {
    return waitFor(async () => {
        await user.click(await getMonacoFilterInput(canvas));
        await assertSuggestions(canvas, (assertion) => assertion.not.toHaveLength(0));
    }, {timeout: 5000});
}

// Stories
export const KestraFilterDefault: Story = {
    decorators: getDecorators()
};

KestraFilterDefault.play = async ({canvasElement, step}) => {
    const canvas = within(canvasElement);
    const user = userEvent.setup();

    await step("filter is empty with default placeholder", async () => {
        await expect(await getMonacoFilterInput(canvas)).toBeEmptyDOMElement();
        await expect(getMonacoFilter(canvas).querySelector("[widgetid=\"editor.widget.placeholderHint\"]"))
            .toHaveTextContent(/^Choose filters$/)
    });

    await new Promise(resolve => setTimeout(resolve, 1000));
    await step(
        "autocompletion pops upon clicking and show only text because no language is set",
        async () => {
            await waitFor(async () => {
                await user.click(await getMonacoFilterInput(canvas));
                await assertSuggestionsValues(canvas, ["text"]);
            }, {timeout: 5000});
        },
    );

    await step(
        "add some text in the filter",
        async () => {
            await user.click(await getMonacoFilterInput(canvas));
            await userEvent.keyboard("test");
            await waitFor(() => assertMonacoFilterContentToBe(canvas, "test"));

            await assertRouteQuery(canvas, {"filters[q][EQUALS]": "test"});
        },
    );
};

export const KestraFilterPlaceholder: Story = {
    decorators: getDecorators(),
    args: {
        placeholder: "Hello Filter"
    }
};

KestraFilterPlaceholder.play = async ({canvasElement, step}) => {
    const canvas = within(canvasElement);

    await step("placeholder should be 'Hello Filter'", async () => {
        await expect(await getMonacoFilterInput(canvas)).toBeEmptyDOMElement();
        await expect(getMonacoFilter(canvas).querySelector("[widgetid=\"editor.widget.placeholderHint\"]"))
            .toHaveTextContent(new RegExp(`^${KestraFilterPlaceholder.args!.placeholder}$`));
    });
};

export const KestraFilterLegacyQuery: Story = {
    decorators: getDecorators(),
    args: {
        legacyQuery: true
    }
};

async function assertRouteQuery(canvas: ReturnType<typeof within>, expectedQuery: LocationQuery = {}) {
    await waitFor(async () => {
        const routeQuery = await parseRouteQuery(canvas);
        return expect(routeQuery).toStrictEqual(expectedQuery)
    }, {timeout: 5000});
}

KestraFilterLegacyQuery.play = async ({canvasElement, step}) => {
    const canvas = within(canvasElement);
    const user = userEvent.setup();

    await step(
        "add some text in the filter",
        async () => {
            await user.click(await getMonacoFilterInput(canvas));
            await userEvent.keyboard("test");
            await waitFor(() => assertMonacoFilterContentToBe(canvas, "test"));
            await assertRouteQuery(canvas, {q: "test"});
        },
    );
};


class TestFilterLanguage extends FilterLanguage {
    static readonly FILTER_KEYS = {
        singleValue: new FilterKeyCompletions(
            [Comparators.EQUALS, Comparators.NOT_EQUALS, Comparators.STARTS_WITH],
            async () => [
                new Completion("First value", "value1"),
                new Completion("Second value", "value2")
            ],
            false,
            ["notCompatibleWithSingleAndNestedAndSelf"]
        ),
        multiValue: new FilterKeyCompletions(
            [Comparators.NOT_EQUALS, Comparators.EQUALS, Comparators.STARTS_WITH],
            async () => [
                new Completion("Another first value", "anotherValue1"),
                new Completion("Another second value", "anotherValue2")
            ],
            true
        ),
        "nested.{key}": new FilterKeyCompletions(
            [Comparators.EQUALS],
            undefined,
            false,
            ["notCompatibleWithSingleAndNestedAndSelf"]
        ),
        notCompatibleWithSingleAndNestedAndSelf: new FilterKeyCompletions(
            [Comparators.EQUALS],
            undefined,
            false,
            ["singleValue", FilterLanguage.withNestedKeyPlaceholder("nested.{key}"), "notCompatibleWithSingleAndNestedAndSelf", "text"]
        )
    };
    static readonly INSTANCE = new TestFilterLanguage();

    private constructor() {
        super("test", TestFilterLanguage.FILTER_KEYS);
    }
}

async function assertSuggestions(canvas: ReturnType<typeof within>, assertion: (assertion: ReturnType<typeof expect<string[]>>) => Promise<void>): Promise<void> {
    const suggestWidget = getMonacoFilter(canvas).querySelector(".suggest-widget");
    if (suggestWidget === null) {
        throw new Error("Waiting for suggest widget to be shown");
    }

    await expect(suggestWidget).toBeVisible();

    const suggestions = [...getMonacoFilter(canvas).querySelectorAll(".monaco-list-row")].map(({textContent}) => textContent);
    return assertion(expect(suggestions));
}

async function assertSuggestionsValues(canvas: ReturnType<typeof within>, expectedSuggestions: string[]) {
    return assertSuggestions(canvas, (assertion) => assertion.toEqual(expectedSuggestions));
}

export const KestraFilterWithLanguage: Story = {
    decorators: getDecorators(),
    args: {
        language: TestFilterLanguage.INSTANCE as FilterLanguage
    }
};

KestraFilterWithLanguage.play = async ({canvasElement, step}) => {
    const canvas = within(canvasElement);
    const user = userEvent.setup();

    await step(
        "autocompletion pops upon clicking and show available keys",
        async () => {
            await waitFor(async () => {
                await user.click(await getMonacoFilterInput(canvas));
                await assertSuggestionsValues(canvas, [...Object.keys(TestFilterLanguage.FILTER_KEYS), "text"]);
            }, {timeout: 5000});
        },
    );

    suggestionWidgetController = {
        accept: window.acceptSuggestion,
        next: window.nextSuggestion
    }

    await step(
        "accepting suggestion should insert the key followed by the first comparator in the filter and proceed to value completion",
        async () => {
            let highlightedSuggest = getMonacoFilter(canvas).querySelector(".monaco-list-row.focused");
            await expect(highlightedSuggest).toHaveTextContent(/^singleValue$/);
            suggestionWidgetController.accept();

            await waitFor(() => assertMonacoFilterContentToBe(canvas, "singleValue="));
            await assertRouteQuery(canvas, {});

            await waitFor(async () => {
                await assertSuggestionsValues(canvas, ["First value", "Second value"]);
            }, {timeout: 5000});
            highlightedSuggest = getMonacoFilter(canvas).querySelector(".monaco-list-row.focused");
            await expect(highlightedSuggest).toHaveTextContent(/^First value$/)

            suggestionWidgetController.next();
            highlightedSuggest = getMonacoFilter(canvas).querySelector(".monaco-list-row.focused");
            await expect(highlightedSuggest).toHaveTextContent(/^Second value$/);
            suggestionWidgetController.accept();

            await assertRouteQuery(canvas, {"filters[singleValue][EQUALS]": "value2"});

            // Back to the initial suggestions as a space is automatically added after the value
            await waitFor(() => assertMonacoFilterContentToBe(canvas, "singleValue=value2 "));

            await waitFor(() => assertSuggestionsValues(canvas, [...Object.keys(TestFilterLanguage.FILTER_KEYS).filter(k => k !== "notCompatibleWithSingleAndNestedAndSelf"), "text"]), {timeout: 5000});
        },
    );
};

export const KestraFilterWithLanguage_MultiValueAnotherComparator: Story = {
    decorators: getDecorators(),
    args: {
        language: TestFilterLanguage.INSTANCE as FilterLanguage
    }
};

KestraFilterWithLanguage_MultiValueAnotherComparator.play = async ({canvasElement, step}) => {
    const canvas = within(canvasElement);
    const user = userEvent.setup();

    await waitForFilterToBeReady(user, canvas);

    suggestionWidgetController = {
        accept: window.acceptSuggestion,
        next: window.nextSuggestion
    }

    await step(
        "accepting suggestion should insert the key followed by the first comparator in the filter and proceed to value completion",
        async () => {
            suggestionWidgetController.next();
            let highlightedSuggest = getMonacoFilter(canvas).querySelector(".monaco-list-row.focused");
            await expect(highlightedSuggest).toHaveTextContent(/^multiValue$/);
            suggestionWidgetController.accept();

            await waitFor(() => assertMonacoFilterContentToBe(canvas, "multiValue!="));
            await assertRouteQuery(canvas, {});

            await waitFor(async () => {
                await assertSuggestionsValues(canvas, ["Another first value", "Another second value"]);
            }, {timeout: 5000});
            highlightedSuggest = getMonacoFilter(canvas).querySelector(".monaco-list-row.focused");
            await expect(highlightedSuggest).toHaveTextContent(/^Another first value$/)

            suggestionWidgetController.accept();
            await waitFor(() => assertMonacoFilterContentToBe(canvas, "multiValue!=anotherValue1,"));
            await assertRouteQuery(canvas, {"filters[multiValue][NOT_EQUALS]": "anotherValue1"});

            highlightedSuggest = getMonacoFilter(canvas).querySelector(".monaco-list-row.focused");
            await expect(highlightedSuggest).toHaveTextContent(/^Another second value$/);
            suggestionWidgetController.accept();

            // No more suggestions as all the values are taken so we add a space
            await waitFor(() => assertMonacoFilterContentToBe(canvas, "multiValue!=anotherValue1,anotherValue2 "));
            // Back to the initial suggestions

            await waitFor(() => assertSuggestionsValues(canvas, [...Object.keys(TestFilterLanguage.FILTER_KEYS), "text"]), {timeout: 5000});

            await assertRouteQuery(canvas, {"filters[multiValue][NOT_IN]": "anotherValue1,anotherValue2"});


        },
    );
};

export const KestraFilterWithLanguage_PopulateValueFromQuery: Story = {
    name: "Keys from query that are not compliant with language should not be added to filter",
    decorators: getDecorators({
        "filters[unknownKey][EQUALS]": "whatever",
        "filters[singleValue][EQUALS]": "unknownValue StillShouldBeAdded",
        "filters[nested][EQUALS][specialKey]": "someValue",
    }),
    args: {
        language: TestFilterLanguage.INSTANCE as FilterLanguage
    }
};

KestraFilterWithLanguage_PopulateValueFromQuery.play = async ({canvasElement, step}) => {
    const canvas = within(canvasElement);

    await step(
        "value should be populated from query at initialization",
        async () => {
            await waitFor(() => assertMonacoFilterContentToBe(canvas, "singleValue=\"unknownValue StillShouldBeAdded\" nested.specialKey=someValue "));
            // verify we kept the unknown value in the query parameters even though we didn't add it to the filter
            await new Promise(resolve => {
                setInterval(resolve, 1100);
            });

            assertRouteQuery(canvas, {
                "filters[unknownKey][EQUALS]": "whatever",
                "filters[singleValue][EQUALS]": "unknownValue StillShouldBeAdded",
                "filters[nested][EQUALS][specialKey]": "someValue"
            });
        }
    );
};

export const KestraFilterWithLanguage_NestedKey: Story = {
    decorators: getDecorators(),
    args: {
        language: TestFilterLanguage.INSTANCE as FilterLanguage
    }
};

KestraFilterWithLanguage_NestedKey.play = async ({canvasElement, step}) => {
    const canvas = within(canvasElement);
    const user = userEvent.setup();

    await step(
        "nested key autocompletion should output `nested.`",
        async () => {
            await waitForFilterToBeReady(user, canvas);

            suggestionWidgetController = {
                accept: window.acceptSuggestion,
                next: window.nextSuggestion
            }
            suggestionWidgetController.next();
            suggestionWidgetController.next();
            const highlightedSuggest = getMonacoFilter(canvas).querySelector(".monaco-list-row.focused");
            await expect(highlightedSuggest).toHaveTextContent(/^nested\.\{key}$/);
            suggestionWidgetController.accept();

            await waitFor(() => assertMonacoFilterContentToBe(canvas, "nested."));
            await assertRouteQuery(canvas, {});
        }
    );

    await step(
        "adding a nested key with a value should add not be colored as error and add the nested key as an extra [...] in the query",
        async () => {
            await userEvent.keyboard("deep.key=\"[[And Value],[[With Spaces]\"");

            await waitFor(() => expect(
                ([...getMonacoFilter(canvas).querySelectorAll(".view-lines .view-line span")] as HTMLElement[])
                    .map(el => isColoredAsError(el))
            ).toSatisfy<boolean[]>(areErrors => areErrors.every(isError => !isError)), {timeout: 5000});
            await assertRouteQuery(canvas, {
                "filters[nested][EQUALS][deep.key]": "[And Value],[With Spaces]"
            });
        },
    );

    // NOTE We can see a bug here as we have no way to distinguish between multiple values and a single value with a comma (because allowed when having quoted value) as of now
    await step(
        "adding a comma and another value should switch the comparator to IN and add the value to the query",
        async () => {
            await userEvent.keyboard(",anotherValue");

            await waitFor(() => expect(
                ([...getMonacoFilter(canvas).querySelectorAll(".view-lines .view-line span")] as HTMLElement[])
                    .map(el => isColoredAsError(el))
            ).toSatisfy<boolean[]>(areErrors => areErrors.every(isError => !isError)), {timeout: 5000});
            await assertRouteQuery(canvas, {
                "filters[nested][IN][deep.key]": "[And Value],[With Spaces],anotherValue"
            });
        },
    );
};

export const KestraFilterWithLanguage_ForbiddenConcurrentKeys: Story = {
    decorators: getDecorators(),
    args: {
        language: TestFilterLanguage.INSTANCE as FilterLanguage
    }
};

function assertNoErrorsInFilter(canvas: ReturnType<typeof within>, expectedFilterContent: string): Promise<void> {
    return waitFor(async () => {
        await assertMonacoFilterContentToBe(canvas, expectedFilterContent);
        return expect(
            ([...getMonacoFilter(canvas).querySelectorAll(".view-lines .view-line span")] as HTMLElement[])
                .map(el => isColoredAsError(el))
        ).toSatisfy<boolean[]>(areErrors => areErrors.every(isError => !isError))
    }, {timeout: 5000});
}

KestraFilterWithLanguage_ForbiddenConcurrentKeys.play = async ({canvasElement, step}) => {
    const canvas = within(canvasElement);
    const user = userEvent.setup();

    await step(
        "adding singleValue filter",
        async () => {
            await waitForFilterToBeReady(user, canvas);

            const filterValue = "singleValue=\"some value\" ";
            await userEvent.keyboard(filterValue);

            await assertNoErrorsInFilter(canvas, filterValue);
        },
    );

    await step(
        "notCompatibleWithSingleAndNestedAndSelf should not show up in autocompletion",
        async () => {
            await waitFor(() => assertSuggestions(canvas, (assertion) => assertion.not.toContain("notCompatibleWithSingleAndNestedAndSelf")));
        },
    );

    await step(
        "cleaning and adding nested.{key} to filter",
        async () => {
            await clearMonacoInput(user, canvas);

            const filterValue = "nested.some.key=\"some value\" ";
            await userEvent.keyboard(filterValue);

            await assertNoErrorsInFilter(canvas, filterValue);
        },
    );

    await step(
        "notCompatibleWithSingleAndNestedAndSelf should not show up in autocompletion",
        async () => {
            await waitFor(() => assertSuggestions(canvas, (assertion) => assertion.not.toContain("notCompatibleWithSingleAndNestedAndSelf")));
        },
    );

    await step(
        "cleaning and asserting that notCompatibleWithSingleAndNestedAndSelf is in autocompletion",
        async () => {
            await waitFor(async () => {
                await clearMonacoInput(user, canvas);
                await user.click(await getMonacoFilterInput(canvas));
                return assertSuggestions(canvas, (assertion) => assertion.toContain("notCompatibleWithSingleAndNestedAndSelf"));
            }, {timeout: 5000})
        },
    );

    await step(
        "adding notCompatibleWithSingleAndNestedAndSelf and assert it's no longer showing in autocompletion",
        async () => {
            const filterValue = "notCompatibleWithSingleAndNestedAndSelf=\"some value\" ";
            await userEvent.keyboard(filterValue);

            await waitFor(() => assertSuggestions(canvas, (assertion) => assertion.not.toContain("notCompatibleWithSingleAndNestedAndSelf")));
        },
    );
};

const monacoFilter = "monaco-filter";

function getMonacoFilter(canvas: ReturnType<typeof within>) {
    return canvas.getByTestId(monacoFilter);
}

async function clearMonacoInput(user: ReturnType<typeof userEvent.setup>, canvas: ReturnType<typeof within>): Promise<void> {
    return user.clear(await getMonacoFilterInput(canvas))
}

function assertMonacoFilterContentToBe(canvas: ReturnType<typeof within>, expectedText: string): Promise<void> {
    // We need to replace non-breaking spaces with regular spaces because Monaco editor uses non-breaking spaces
    return expect(getMonacoFilter(canvas)).toHaveTextContent(expectedText, {normalizeWhitespace: true});
}

function getMonacoFilterInput(canvas: ReturnType<typeof within>): Promise<HTMLElement> {
    return waitFor(() => within(getMonacoFilter(canvas)).getByRole("textbox"));
}
