<template>
    <KsButton
        :tag="RouterLink"
        :to="subflowExecutionRoute(execution)"
        size="small"
        :icon="OpenInNew"
        :aria-label="iconOnly ? $t('gantt_panel.open_execution') : undefined"
        :title="iconOnly ? $t('gantt_panel.open_execution') : undefined"
        data-test="gantt-open-execution"
        @click.stop
    >
        <template v-if="!iconOnly">{{ $t("gantt_panel.open_execution") }}</template>
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
        iconOnly?: boolean;
    }>()
</script>
