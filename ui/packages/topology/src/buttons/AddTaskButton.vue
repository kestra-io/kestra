<template>
    <div class="add-task-div">
        <Plus v-if="addTask" alt="add task icon" />
    </div>
</template>

<script setup lang="ts">
    import Plus from "vue-material-design-icons/Plus.vue"

    defineOptions({
        name: "AddTaskButton",
    })

    defineProps<{addTask?: boolean}>()
</script>

<style scoped lang="scss">
.add-task-div {
    display: flex;
    justify-content: center;
    align-items: center;
    border-radius: var(--ks-border-radius);
    margin: 0.2rem;
    width: 25px;
    height: 25px;
    border: 0.4px solid var(--ks-border-primary);
    background-color: var(--ks-background-card);
    cursor: pointer;

    &.text-danger {
        border-color: var(--ks-border-error);
    }

    &:hover {
        background-color: var(--ks-button-background-secondary-hover);
    }
}
</style>
