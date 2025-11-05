/* eslint-disable vue/one-component-per-file */
import {defineComponent, getCurrentInstance, onMounted, ref} from "vue";
import {vueRouter} from "storybook-vue3-router";
import {createPinia} from "pinia";

import {
    FLOW,
    EXECUTION,
    NAMESPACE,
} from "../../../../src/components/dependencies/utils/types";
import {useDependencies} from "../../../../src/components/dependencies/composables/useDependencies";
import Table from "../../../../src/components/dependencies/components/Table.vue";

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
        subtype: {type: Number, default: FLOW},
    },
    setup(props) {
        const app = getCurrentInstance()?.appContext.app;
        if (app) app.use(createPinia());

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
    components: {GraphWrapper},
    template: "<GraphWrapper :subtype=\"FLOW\" />",
    setup() {
        return {FLOW};
    },
});

export const ExecutionGraph = () => ({
    components: {GraphWrapper},
    template: "<GraphWrapper :subtype=\"EXECUTION\" />",
    setup() {
        return {EXECUTION};
    },
});

export const NamespaceGraph = () => ({
    components: {GraphWrapper},
    template: "<GraphWrapper :subtype=\"NAMESPACE\" />",
    setup() {
        return {NAMESPACE};
    },
});
