<template>
    <TaskObject
        :model-value
        :schema
        :definitions
        @update:model-value="(v) => update(v)"
        :properties="computedProperties"
        expand-optional
    />
    <Save @click="closePanel" class="w-100 mt-3" />
</template>

<script>
    import {h} from "vue";

    import Task from "./Task";

    import TaskObject from "./TaskObject.vue";
    import Save from "../../code/components/Save.vue";

    import {
        BREADCRUMB_INJECTION_KEY,
        PANEL_INJECTION_KEY,
    } from "../../code/injectionKeys";

    export default {
        mixins: [Task],
        inject: {
            panel: {from: PANEL_INJECTION_KEY},
            breadcrumbs: {from: BREADCRUMB_INJECTION_KEY},
        },
        props: {
            metadataInputs: {type: Boolean, default: false},
            previousPanel: {type: Object, default: () => ({})},
        },
        components: {TaskObject, Save},
        data() {
            return {
                currentTask: {},
            };
        },
        computed: {
            computedProperties() {
                const type = this.schema.$ref.split("/").pop();
                return this.definitions[type]?.properties;
            },
        },
        methods: {
            update(v) {
                const updated = {
                    ...this.task,
                    [this.root]: {...this.currentTask[this.root], ...v},
                };
                this.currentTask = updated;
                this.onInput(this.metadataInputs ? updated : v);
            },
            closePanel() {
                this.panel = this.previousPanel ? h(this.previousPanel) : null;
                this.breadcrumbs.pop();
            },
        },
    };
</script>
