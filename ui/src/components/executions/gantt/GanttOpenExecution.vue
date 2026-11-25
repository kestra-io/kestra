<template>
    <KsButton
        :tag="RouterLink"
        :to="subflowExecutionRoute(execution)"
        size="small"
        :icon="OpenInNew"
        :aria-label="iconOnly ? label : undefined"
        :title="iconOnly ? label : undefined"
        data-test="gantt-open-execution"
        @click.stop
    >
        <template v-if="!iconOnly">{{ label }}</template>
    </KsButton>
</template>

<script setup lang="ts">
    import {RouterLink} from "vue-router"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import {KsButton} from "@kestra-io/design-system"
    import {subflowExecutionRoute} from "../../../utils/subflowExecutions"
    import type {ExecutionRef} from "./ganttTree"

    defineProps<{
        execution: ExecutionRef;
        label: string;
        iconOnly?: boolean;
    }>()
</script>
