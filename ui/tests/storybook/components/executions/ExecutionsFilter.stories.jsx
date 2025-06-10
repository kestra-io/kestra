import {useStore} from "vuex";
import {vueRouter} from "storybook-vue3-router";
import Executions from "../../../../src/components/executions/Executions.vue";
import fixtureS from "./Executions-s.fixture.json";
import {expect, userEvent, waitFor, within} from "storybook/test";
import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import {isColoredAsError} from "../../utils/monacoUtils.js";

function getDecorators(executionsSearchData) {
    return [
        () => {
            return {
                setup() {
                    const store = useStore();
                    store.commit("auth/setUser", {
                        id: "123",
                        firstName: "John",
                        lastName: "Doe",
                        email: "john.doe@example.com",
                        isAllowed: () => true,
                        hasAnyActionOnAnyNamespace: () => true,
                    });
                    store.commit("misc/setConfigs", {
                        hiddenLabelsPrefixes: ["system_"],
                    });
                    store.$http = {
                        get: async (uri, _params) => {
                            if (uri.endsWith("executions/search")) {
                                console.log("uri", uri);
                                // query params are available here if we want to make tests with them
                                // console.log("params", params);
                                return Promise.resolve({
                                    data: executionsSearchData,
                                });
                            }
                            return Promise.resolve({data: []});
                        },
                        post: async (uri) => {
                            console.log("post request", uri);

                            if (uri.includes("/dashboards/charts/preview")) {
                                return Promise.resolve({}); // empty chart
                            }
                            throw new Error(
                                "Unhandled fixture Request POST: " + uri,
                            );
                        },
                    };
                },
                template: "<div style='margin:2rem'><story /></div>",
            };
        },
        vueRouter(
            [
                {
                    path: "/",
                    name: "home",
                    component: {template: "<div>home</div>"},
                },
                {
                    path: "/flows/update/:namespace/:id?/:flowId?",
                    name: "flows/update",
                    component: {template: "<div>updateflows</div>"},
                },
                {
                    path: "/executions/update/:namespace/:id?/:flowId?",
                    name: "executions/update",
                    component: {template: "<div>executions</div>"},
                },
                {
                    path: "/executions/:id?/:flowId?",
                    name: "executions/list",
                    component: {template: "<div>executions</div>"},
                },
            ],
            {
                initialRoute: "/executions/123/645",
            },
        ),
    ];
}

// Story configuration
export default {
    title: "Components/Executions",
    component: Executions,
};

// Stories
export const FilterExecutions = {
    decorators: getDecorators(fixtureS),
    args: {
        hidden: [],
        statuses: [],
        isReadOnly: false,
        topbar: false,
        filter: true,
    },
};

FilterExecutions.play = async ({canvasElement, step}) => {
    const canvas = within(canvasElement);
    const user = userEvent.setup();

    await step("filter should contains \"timeRange\" by default", async () => {
        await waitFor(() => expect(getMonacoFilter(canvas)).toHaveTextContent("timeRange="), {timeout: 5000});
    });

    await step(
        "clearing and adding a namespace filter with keyboard",
        async () => {
            await user.click(getMonacoFilterInput(canvas));
            await clearMonacoInput();
            await userEvent.keyboard("namespace=io.kestra");
            await refreshMonacoFilter(canvas);

            await waitFor(() =>
                expect(getMonacoFilter(canvas)).toHaveTextContent(
                    "namespace=io.kestra",
                ),
            );
        },
    );

    await step("adding an additional flowId filter with keyboard", async () => {
        await waitFor(() =>
            expect(getMonacoFilter(canvas)).toHaveTextContent(
                "namespace=io.kestra",
            ),
        );

        await user.click(getMonacoFilterInput(canvas));
        await userEvent.keyboard("{End}");
        await userEvent.keyboard(spaceBarKey);
        await userEvent.keyboard("flowId=123");
        await refreshMonacoFilter(canvas);

        await waitFor(() =>
            expect(getMonacoFilter(canvas)).toHaveTextContent(
                "namespace=io.kestra",
            ),
        );
        await waitFor(() =>
            expect(getMonacoFilter(canvas)).toHaveTextContent("flowId=123"),
        );
    });

    await step("unknown field should be displayed red", async () => {
        await user.click(getMonacoFilterInput(canvas));
        await clearMonacoInput();
        await userEvent.keyboard("an-unknown-field=q2132");
        await refreshMonacoFilter(canvas);

        await waitFor(() =>
            expect(getMonacoFilter(canvas)).toHaveTextContent(
                "an-unknown-field=q2132",
            ),
        );
        await waitFor(() =>
            expect(
                isColoredAsError(within(getMonacoFilter(canvas)).getByText(
                    "an-unknown-field=q2132",
                ))
            ).toBeTruthy(),
        );
    });

    await step(
        "unknown field should be marked as invalid internally by Monaco",
        async () => {
            await user.click(getMonacoFilterInput(canvas));
            await clearMonacoInput();
            await userEvent.keyboard("an-unknown-field=q2222222222");
            await refreshMonacoFilter(canvas);

            await waitFor(() =>
                expect(getMonacoFilter(canvas)).toHaveTextContent(
                    "an-unknown-field=q2222222222",
                ),
            );
            const model = monaco.editor.getModels()[0];
            const tokens = monaco.editor.tokenize(
                model.getValue(),
                "executions-filter",
            );
            await expect(tokens).toBeDefined();
            await expect(tokens[0][0].type).toContain("executions-filter");
            await expect(tokens[0][0].type).toContain("invalid");
        },
    );
};
// TODO test from query route !!!!

// Helpers and constants
const spaceBarKey = "{ }";
const triggerRefreshButton = "trigger-refresh-button";
const monacoFilter = "monaco-filter";

function getMonacoFilter(canvas) {
    return canvas.getByTestId(monacoFilter);
}

function getMonacoFilterInput(canvas) {
    const monacoFilter = getMonacoFilter(canvas);
    return within(monacoFilter).getByRole("textbox");
}

async function clearMonacoInput() {
    await userEvent.keyboard("{Control>}a{/Control}{Delete}");
}

async function refreshMonacoFilter(canvas) {
    await userEvent.click(canvas.getByTestId(triggerRefreshButton));
}
