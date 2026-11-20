<template>
    <div class="paused-wrapper">
        <KsAlert id="paused" type="warning" :closable="false" fill>
            <template #title>
                <span class="title">{{ $t("execution_paused") }}</span>
            </template>

            <div class="description">
                <p>{{ $t("execution_paused_description") }}</p>
            </div>
        </KsAlert>
    </div>
</template>

<script setup lang="ts">
    import {Execution} from "../../../../../stores/executions"

    defineProps<{execution: Execution}>()
</script>

<style scoped>
    .paused-wrapper {
        position: relative;
    }

    .description {
        color: var(--ks-text-primary);
        margin: 0;

        p {
            margin: 0;
        }
    }

    #paused {
        overflow: hidden;

        & .title {
            display: flex;
            justify-content: space-between;
            align-items: center;
            font-size: var(--kel-alert-title-font-size);
            line-height: 1.5;
            color: var(--ks-text-warning);
        }
    }
</style>
