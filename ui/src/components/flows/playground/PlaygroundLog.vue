<template>
    <div class="playground-log">
        <button
            v-for="execution in executions"
            :key="execution.id"
            @click="() => executionsStore.execution = execution"
            :class="{active: executionsStore.execution?.id === execution.id}"
        >
            <p>{{ date(execution.state.startDate) }}</p>
            <p class="playground-duration">
                {{ humanizeDuration(execution.state.duration) }}
            </p>
            <div class="playground-status">
                <Status :status="execution.state.current" size="small" />
            </div>
        </button>
    </div>
</template>

<script setup lang="ts">
    import {Status} from "@kestra-io/ui-libs";
    import {date, humanizeDuration} from "../../../utils/filters";
    import {Execution, useExecutionsStore} from "../../../stores/executions";

    const executionsStore = useExecutionsStore();

    defineProps<{
        executions: Execution[];
    }>();

    defineEmits<{
        (e: "click", executionId: string): void;
    }>();
</script>

<style scoped lang="scss">
    .playground-log{
        display: flex;
        flex-direction: column;
        gap: .5rem;
        padding: 0.5rem 0.5rem;
    }

    .playground-log > button{
        display: grid;
        border: none;
        width: 284px;
        background-color: var(--ks-background-panel);
        text-align: left;
        padding: .3rem .5rem;
        font-size: 0.8rem;
        border-radius: 5px;
        grid-template-columns: 1fr 80px;
        grid-template-rows: 1fr 1fr;
        grid-template-areas:
            ". status"
            "duration status";
        p{
            margin: 0;
            padding: 0;
        }
        &.active{
            background-color: var(--ks-background-card-hover);
        }
    }
    .playground-status {
        grid-area: status;
        display: flex;
        align-items: center;
        justify-content: end;
        text-align: right;;
    }
    .playground-duration {
        grid-area: duration;
        color: var(--ks-content-secondary);
    }
</style>