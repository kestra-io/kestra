<template>
    <TaskObject
        :model-value
        :schema
        :definitions
        @update:model-value="(e) => update(e)"
        :properties="definitions[Object.keys(definitions)?.[0]]?.properties"
        expand-optional
    />
    <Save @click="closePanel" what="input" class="w-100 mt-3" />
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
            previousPanel: {type: Object, default: () => ({})},
        },
        components: {TaskObject, Save},
        methods: {
            update(e) {
                this.onInput({...this.task, [this.root]: e});
            },
            closePanel() {
                this.panel = this.previousPanel ? h(this.previousPanel) : null;
                this.breadcrumbs.pop();
            },
        },
    };
</script>