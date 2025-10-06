<template>
    <EmptyTemplate class="queued">
        <img src="../../assets/queued_visual.svg" alt="Queued Execution">
        <h5 class="mt-4 fw-bold">
            {{ $t('execution_status') }} 
            <span 
                class="ms-2 px-2 py-1 rounded fs-7 fw-normal" 
                :style="getStyle(execution.state.current)"
            >
                {{ execution.state.current }}
            </span>
        </h5>
        <p class="mt-4 mb-0">
            {{ $t('no_tasks_running') }}
        </p>
        <p>
            {{ $t('execution_starts_progress') }}
        </p>
    </EmptyTemplate>
</template>

<script setup>
    import EmptyTemplate from "../layout/EmptyTemplate.vue";

    defineProps({
        execution: {
            type: Object,
            required: true
        }
    });

    const getStyle = (state) => ({
        color: `var(--ks-content-${state.toLowerCase()})`,
        border: `1px solid var(--ks-border-${state.toLowerCase()})`,
        backgroundColor: `var(--ks-background-${state.toLowerCase()})`
    })
</script>

<style lang="scss" scoped>
.queued {
    margin-top: -2rem;
}

p {
    color: var(--ks-content-secondary);
}
</style>
