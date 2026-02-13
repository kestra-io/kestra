/* eslint-disable vue/one-component-per-file */
import {defineComponent, onMounted, ref} from "vue";
import {vueRouter} from "storybook-vue3-router";

import {
    FLOW,
    EXECUTION,
    NAMESPACE,
} from "../../../../src/components/dependencies/utils/types";
import {useDependencies} from "../../../../src/components/dependencies/composables/useDependencies";
import Table from "../../../../src/components/dependencies/components/Table.vue";

import cytoscape from "cytoscape";

cytoscape.warnings(false)



export default {
    title: "Dependencies/Graph",
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
};

const GraphWrapper = defineComponent({
    name: "DependenciesGraphStoryWrapper",
    props: {
        subtype: {type: String, default: FLOW},
    },
    setup(props) {
        onMounted(async () => {
            if (props.subtype === EXECUTION) {
                const {useExecutionsStore} = await import(
                    "../../../../src/stores/executions"
                );
                const executionsStore = useExecutionsStore();
                executionsStore.followExecutionDependencies = () => {
                    return {
                        close: () => void 0,
                        onmessage: null,
                        onerror: null,
                    };
                };
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
        } = useDependencies(container, props.subtype, "", params, true);

        return () => (
            <div style="display:flex; gap:12px; height:680px;">
                <div style="flex:1; position:relative; min-width:480px;">
                    <div
                        v-loading={isRendering.value}
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
                    <Table
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
