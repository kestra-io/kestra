<template>
    <ks-dropdown
        splitButton
        @visible-change="playgroundStore.dropdownOpened = $event"
        :buttonProps="{class: 'el-button--playground'}"
        @click="playgroundStore.runUntilTask(taskId)"
        :disabled="!playgroundStore.readyToStart"
    >
        <ks-icon><Play /></ks-icon>
        <span>{{ $t('playground.run_task') }}</span>
        <template #dropdown>
            <ks-dropdown-menu>
                <ks-dropdown-item :icon="Play" @click="playgroundStore.runUntilTask(taskId)">
                    {{ $t('playground.run_this_task') }}
                </ks-dropdown-item>
                <ks-dropdown-item :icon="PlayBoxMultiple" @click="playgroundStore.runUntilTask(taskId, true)">
                    {{ $t('playground.run_task_and_downstream') }}
                </ks-dropdown-item>
            </ks-dropdown-menu>
        </template>
    </ks-dropdown>
</template>

<script setup lang="ts">
    import {usePlaygroundStore} from "../../stores/playground";
    import Play from "vue-material-design-icons/Play.vue";
    import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple.vue";

    const playgroundStore = usePlaygroundStore();

    defineProps<{
        taskId?: string;
    }>();
</script>

<style scoped lang="scss">
.toggle{
    margin-right: 1rem;
}
</style>
