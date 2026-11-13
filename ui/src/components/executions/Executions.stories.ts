import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {vueRouter} from "storybook-vue3-router"
import Executions from "./Executions.vue"
import {useMiscStore} from "override/stores/misc"
import {useAuthStore} from "override/stores/auth"
import fixture from "../../../tests/storybook/components/executions/Executions.fixture.json"
import fixtureS from "../../../tests/storybook/components/executions/Executions-s.fixture.json"
import {setMockClient, type AxiosLikeClient, type PagedResultsApiLightExecution} from "@kestra-io/kestra-sdk"
import {mockResponse, mockStoryApiRoutes} from "../../../.storybook/apiMock"
import {useFlowStore} from "../../stores/flow"
import {expect, userEvent, waitFor, within} from "storybook/test"

function getDecorators(data: PagedResultsApiLightExecution, initialRoute = "/executions/123/645") {
    return [
        () => {
            return {
                setup () {
                    const authStore = useAuthStore()
                    const miscStore = useMiscStore()
                    const flowStore = useFlowStore()

                    authStore.user = {
                        id: "123",
                        firstName: "John",
                        lastName: "Doe",
                        email: "john.doe@example.com",
                        isAllowed: () => true,
                        hasAnyAction: () => true,
                        hasAnyActionOnAnyNamespace: () => true,
                    } as unknown as typeof authStore.user
                    if (initialRoute.startsWith("/flows/update")) {
                        flowStore.flow = {id: "airbyte-sync", namespace: "company.team", source: ""}
                    }
                    miscStore.configs = {
                        hiddenLabelsPrefixes: ["system_"],
                    }
                    mockStoryApiRoutes({"GET /executions/search": data})

                    const axios: Partial<AxiosLikeClient> = {}
                    axios.get = async function<T>() {
                        return mockResponse<T>([])
                    }
                    setMockClient(axios)
                },
                template: "<div style='margin:2rem'><story /></div>",
            }
        },
        vueRouter([
        {
            path: "/",
            name: "home",
            component: {template: "<div>home</div>"},
        },
          {
            path: "/flows/update/:namespace/:id?/:flowId?",
            name: "flows/update",
            component: {template: "<div>updateflows</div>"},
          },{
            path: "/executions/update/:namespace/:id?/:flowId?",
            name: "executions/update",
            component: {template: "<div>executions</div>"},
          },
          {
            path: "/executions/:id?/:flowId?",
            name: "executions/list",
            component: {template: "<div>executions</div>"},
          },
          {
            path: "/namespaces/edit/:id",
            name: "namespaces/update",
            component: {template: "<div>namespace</div>"},
          },
        ], {
            initialRoute,
        }),
    ]
}

// Story configuration
const meta: Meta<typeof Executions> = {
    title: "Components/Executions",
    component: Executions,
    parameters: {
        layout: "fullscreen",
    },
}

export default meta

type Story = StoryObj<typeof Executions>

// Stories
export const SmallData: Story = {
    decorators: getDecorators(fixtureS as unknown as PagedResultsApiLightExecution),
    args: {
        hidden: [],
        statuses: [],
        isReadOnly: false,
        embed: true,
        topbar: false,
        filter: false,
    },
}

export const BulkActionEligibility: Story = {
    decorators: getDecorators(fixtureS as unknown as PagedResultsApiLightExecution),
    args: {
        hidden: [],
        statuses: [],
        isReadOnly: false,
        embed: true,
        topbar: false,
        filter: false,
    },
    play: async ({canvasElement}) => {
        const canvas = within(canvasElement)
        const runningRow = canvas.getByText("Running", {exact: true}).closest<HTMLTableRowElement>("tr")
        const failedRow = canvas.getByText("Failed", {exact: true}).closest<HTMLTableRowElement>("tr")

        if (!runningRow || !failedRow) {
            throw new Error("Expected execution rows were not rendered")
        }

        const runningCheckbox = within(runningRow).getByRole("checkbox")
        await userEvent.click(runningCheckbox)
        await userEvent.click(within(failedRow).getByRole("checkbox"))

        const restart = await canvas.findByRole("button", {name: "Restart"})
        await expect(restart).toBeDisabled()

        await userEvent.click(runningCheckbox)
        await waitFor(() => expect(restart).toBeEnabled())
    },
}

export const BiggerData: Story = {
    decorators: getDecorators(fixture as unknown as PagedResultsApiLightExecution),
    args: {
        hidden: [],
        statuses: [],
        topbar: false,
        filter: false,
    },
}

export const FlowExecutionsTab: Story = {
    decorators: getDecorators(fixtureS as unknown as PagedResultsApiLightExecution, "/flows/update/company.team/airbyte-sync"),
    args: {
        hidden: [],
        statuses: [],
        topbar: false,
        filter: false,
        flowId: "airbyte-sync",
        namespace: "company.team",
    },
    play: async ({canvasElement}) => {
        const canvas = within(canvasElement)
        expect((await canvas.findAllByText("airbyte-sync")).length).toBeGreaterThan(0)
        expect((await canvas.findAllByText("company.team")).length).toBeGreaterThan(0)
    },
}

