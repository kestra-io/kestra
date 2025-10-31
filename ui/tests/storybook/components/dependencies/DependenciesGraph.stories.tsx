import {defineComponent, getCurrentInstance, onMounted, ref} from "vue";
import {createPinia} from "pinia";

import {FLOW, EXECUTION, NAMESPACE, type Node} from "../../../../src/components/dependencies/utils/types";
import {useDependencies} from "../../../../src/components/dependencies/composables/useDependencies";
import Table from "../../../../src/components/dependencies/components/Table.vue";
import {getDependencies} from "../../../fixtures/dependencies/getDependencies";

// Storybook meta
export default {
    title: "Components/Dependencies/Graph",
};

// Minimal wrapper to render the graph + table using testing data
const GraphWrapper = defineComponent<{ subtype: typeof FLOW | typeof EXECUTION | typeof NAMESPACE } >({
    name: "DependenciesGraphStoryWrapper",
    props: {
        subtype: {type: Number, default: FLOW},
    },
    setup(props) {
        // Install Pinia in Storybook app context (required by stores used in composable)
        const app = getCurrentInstance()?.appContext.app;
        if (app) app.use(createPinia());

        // Mock followExecutionDependencies to avoid real SSE when subtype is EXECUTION
        onMounted(() => {
            if (props.subtype === EXECUTION) {
                // Lazy import to avoid circular deps in SSR
                // eslint-disable-next-line @typescript-eslint/no-var-requires
                const {useExecutionsStore} = require("../../../../src/stores/executions");
                const executionsStore = useExecutionsStore();
                executionsStore.followExecutionDependencies = () => {
                    return {
                        close: () => void 0,
                        onmessage: null,
                        onerror: null,
                    } as unknown as EventSource;
                };
            }
        });

        const container = ref<HTMLElement | null>(null);

        // Dummy params for composable signature; ignored in testing mode
        const params = {id: "flow-a", flowId: "flow-a", namespace: "ns.a"} as any;

        const {getElements, isRendering, selectedNodeID, selectNode, handlers} = useDependencies(
            container,
            props.subtype,
            "",
            params,
            true // isTesting
        );

        return () => (
            <div style="display:flex; gap:12px; height:680px;">
                <div style="flex:1; position:relative; min-width:480px;">
                    <div v-loading={isRendering.value} ref={container} style="height:100%; overflow:hidden; background:transparent;" />
                    <div style="position:absolute; bottom:10px; left:10px; display:flex; flex-direction:column; gap:4px;">
                        <button title="Zoom in" style="width:2rem; height:2rem;" onClick={handlers.zoomIn}>+</button>
                        <button title="Zoom out" style="width:2rem; height:2rem;" onClick={handlers.zoomOut}>-</button>
                        <button title="Clear selection" style="width:2rem; height:2rem;" onClick={handlers.clearSelection}>×</button>
                        <button title="Fit view" style="width:2rem; height:2rem;" onClick={handlers.fit}>□</button>
                    </div>
                </div>
                <div style="width:380px; height:100%;">
                    <Table elements={getElements()} selected={selectedNodeID.value as Node["id"]} onSelect={selectNode} />
                </div>
            </div>
        );
    }
});

// Template
const Template = (args: { subtype: typeof FLOW | typeof EXECUTION | typeof NAMESPACE }) => ({
    components: {GraphWrapper},
    setup() {
        return {args};
    },
    template: '<GraphWrapper v-bind="args" />'
});

export const FlowGraph = Template.bind({});
FlowGraph.args = {subtype: FLOW};

export const ExecutionGraph = Template.bind({});
ExecutionGraph.args = {subtype: EXECUTION};

// Smaller dataset variant for quicker local iteration
export const FlowGraphSmall = () => ({
    components: {Table},
    setup() {
        const elements = getDependencies({subtype: FLOW, total: 40});
        const selected = ref<Node["id"] | undefined>(undefined);
        return {elements, selected};
    },
    template: `
    <div style="display:flex; gap:12px; height:520px;">
      <div style="flex:1; min-width:480px; display:flex; align-items:center; justify-content:center; color:var(--ks-text-muted);">
        Graph is rendered in FlowGraph/ExecutionGraph stories.
      </div>
      <div style="width:380px; height:100%;">
        <Table :elements="elements" :selected="selected" @select="selected = $event" />
      </div>
    </div>`
});


