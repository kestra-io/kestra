import {useStore} from "vuex";
import {vueRouter} from "storybook-vue3-router";
import Executions from "../../../../src/components/executions/Executions.vue";
import fixture from "./Executions.fixture.json"
import fixtureS from "./Executions-s.fixture.json"

function getDecorators(data) {
    return [
        () => {
            return {
                setup () {
                    const store = useStore()
                    store.commit("auth/setUser", {
                        id: "123",
                        firstName: "John",
                        lastName: "Doe",
                        email: "john.doe@example.com",
                        isAllowed: () => true,
                        hasAnyActionOnAnyNamespace: () => true,
                    })
                    store.commit("misc/setConfigs", {
                        hiddenLabelsPrefixes: ["system_"]
                    })
                    store.$http = {
                        get(a) {
                            if (a.endsWith("executions/search")) {
                                return Promise.resolve({
                                    data 
                                })
                            }
                            return Promise.resolve({data: []})
                        },
                    }
                },
                template: "<div style='margin:2rem'><story /></div>"
            }
        },
        vueRouter([
        {
            path: "/",
            name: "home",
            component: {template: "div>home</div>"}
        },
          {
            path: "/flows/update/:namespace/:id?/:flowId?",
            name: "flows/update",
            component: {template: "div>updateflows</div>"}
          },{
            path: "/executions/update/:namespace/:id?/:flowId?",
            name: "executions/update",
            component: {template: "div>executions</div>"}
          },
          {
            path: "/executions/:id?/:flowId?",
            name: "executions/list",
            component: {template: "div>executions</div>"}
          }
        ], {
            initialRoute: "/executions/123/645"
        }),
    ]
}

// Story configuration
export default {
    title: "Components/Executions",
    component: Executions,
    parameters: {
        layout: "fullscreen"
    }
};


// Stories
export const SmallData = {
    decorators: getDecorators(fixtureS),
    args: {
        hidden: [],
        statuses: [],
        isReadOnly: false,
        embed: true,
        topbar: false,
        filter: false
    }
};

export const BiggerData = {
    decorators: getDecorators(fixture),
    args: {
        hidden: [],
        statuses: [],
        topbar: false,
        filter: false
    }
};