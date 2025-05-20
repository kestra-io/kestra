<template>
    <el-input :model-value="JSON.stringify(values)">
        <template #append>
            <el-button :icon="TextSearch" @click="openPanel" />
        </template>
    </el-input>
</template>

<script setup>
    import {h} from "vue";
    import TextSearch from "vue-material-design-icons/TextSearch.vue";

    import {
        BREADCRUMB_INJECTION_KEY,
        PANEL_INJECTION_KEY,
    } from "../../code/injectionKeys";
</script>

<script>
    import Task from "./Task";
    import TaskComplexContent from "./TaskComplexContent.vue";

    export default {
        mixins: [Task],
        inject: {
            panel: {from: PANEL_INJECTION_KEY},
            breadcrumbs: {from: BREADCRUMB_INJECTION_KEY},
        },
        methods: {
            openPanel() {
                const current = {...this.panel};

                this.panel = h(TaskComplexContent, {
                    modelValue: this.modelValue,
                    schema: this.schema,
                    definitions: this.definitions,
                    task: this.task,
                    root: this.root,
                    "onUpdate:modelValue": this.onInput,
                    previousPanel: current,
                });

                this.breadcrumbs.push({label: this.root});
            },
        },
    };
</script>