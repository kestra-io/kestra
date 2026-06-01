import {defineComponent, onMounted, PropType, ref} from "vue";
import { Meta } from "@storybook/vue3-vite";
import {vueRouter} from "storybook-vue3-router";

import {
    FLOW,
    EXECUTION,
    NAMESPACE,
    Types,
} from "../../../../src/components/dependencies/utils/types";
import {useDependencies} from "../../../../src/components/dependencies/composables/useDependencies";
import DependenciesTable from "../../../../src/components/dependencies/components/Table.vue";
import { useExecutionsStore } from "../../../../src/stores/executions.ts";
import { getDependencies, getRandomNumber } from "../../../fixtures/dependencies/getDependencies.ts";


const meta: Meta<typeof DependenciesTable> = {
    title: "components/dependencies/Table",
    component: DependenciesTable,
    decorators: [
        vueRouter([
            {path: "/", name: "home", component: {template: "<div />"}},
            {
                path: "/flows/:namespace/:id",
                name: "flows/update",
                component: {template: "<div />"},
            },
        ]),
    ],
}

export default meta

const GraphWrapper = defineComponent({
    name: "DependenciesGraphStoryWrapper",
    props: {
        subtype: {type: String as PropType<Types | undefined>, default: FLOW},
    },
    setup(props) {
        onMounted(async () => {
            if (props.subtype === EXECUTION) {
                // mock the followExecutionDependencies method to prevent actual API calls 
                // and WebSocket connections during testing
                const executionsStore = useExecutionsStore() as any;
                executionsStore.followExecutionDependencies = () => {
                    return {
                        close: () => void 0,
                        onmessage: null,
                        onerror: null,
                    };
                }
            }
        });

        const container = ref(null);
        const params = {id: "flow-a", flowId: "flow-a", namespace: "ns.a"};

        const {
            getElements,
            isRendering,
            selectedNodeID,
            selectNode,
            handlers,
        } = useDependencies(
            container, 
            props.subtype, 
            "", 
            params, 
            async () => {
                const res = await getDependencies({subtype: props.subtype || FLOW});
                return {
                    data: res, 
                    count: getRandomNumber(1, 100)
                };
            }
        );

        return () => (
            <div style="display:flex; gap:12px; height:680px;">
                <div style="flex:1; position:relative; min-width:480px;">
                    <div
                        v-ks-loading={isRendering.value}
                        ref={container}
                        style="height:100%; overflow:hidden; background:transparent;"
                    />
                    <div style="position:absolute; bottom:10px; left:10px; display:flex; flex-direction:column; gap:4px;">
                        <button
                            title="Zoom in"
                            style="width:2rem; height:2rem;"
                            onClick={handlers.zoomIn}
                        >
                            +
                        </button>
                        <button
                            title="Zoom out"
                            style="width:2rem; height:2rem;"
                            onClick={handlers.zoomOut}
                        >
                            -
                        </button>
                        <button
                            title="Clear selection"
                            style="width:2rem; height:2rem;"
                            onClick={handlers.clearSelection}
                        >
                            ×
                        </button>
                        <button
                            title="Fit view"
                            style="width:2rem; height:2rem;"
                            onClick={handlers.fit}
                        >
                            □
                        </button>
                    </div>
                </div>
                <div style="width:380px; height:100%;">
                    <DependenciesTable
                        elements={getElements()}
                        selected={selectedNodeID.value}
                        onSelect={selectNode}
                    />
                </div>
            </div>
        );
    },
});

export const FlowGraph = () => ({
    setup() {
        return () => <GraphWrapper subtype={FLOW} />
    },
});

export const ExecutionGraph = () => ({
    setup() {
        return () => <GraphWrapper subtype={EXECUTION} />
    },
});

export const NamespaceGraph = () => ({
    setup() {
        return () => <GraphWrapper subtype={NAMESPACE} />;
    },
});
