import {vi} from "vitest"

// executionsStore.findExecutions() calls ExecutionsAPI.searchExecutions() directly, which goes
// through the SDK's own internal client rather than the axios instance setMockClient() swaps -
// so it has to be intercepted at the submodule level.
const mockState = vi.hoisted(() => ({data: {results: [], total: 0} as PagedResultsApiLightExecution}))
vi.mock("@kestra-io/kestra-sdk/executions", () => ({
    searchExecutions: async () => mockState.data,
}))

import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {vueRouter} from "storybook-vue3-router"
import Executions from "../../../../src/components/executions/Executions.vue"
import {useMiscStore} from "override/stores/misc"
import {useAuthStore} from "override/stores/auth"
import fixture from "./Executions.fixture.json"
import fixtureS from "./Executions-s.fixture.json"
import {setMockClient, type PagedResultsApiLightExecution} from "@kestra-io/kestra-sdk"

function getDecorators(data: PagedResultsApiLightExecution) {
    return [
        () => {
            return {
                setup () {
                    const authStore = useAuthStore()
                    const miscStore = useMiscStore()

                    authStore.user = {
                        id: "123",
                        firstName: "John",
                        lastName: "Doe",
                        email: "john.doe@example.com",
                        isAllowed: () => true,
                        hasAnyActionOnAnyNamespace: () => true,
                    } as unknown as typeof authStore.user
                    miscStore.configs = {
                        hiddenLabelsPrefixes: ["system_"],
                    }
                    mockState.data = data

                    const axios: any = {}
                    axios.get = function() {
                        return Promise.resolve({data: []})
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
            initialRoute: "/executions/123/645",
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

export const BiggerData: Story = {
    decorators: getDecorators(fixture as unknown as PagedResultsApiLightExecution),
    args: {
        hidden: [],
        statuses: [],
        topbar: false,
        filter: false,
    },
}
