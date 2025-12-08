<template>
    <el-dropdown
        splitButton
        @visible-change="playgroundStore.dropdownOpened = $event"
        :buttonProps="{class: 'el-button--playground'}"
        @click="playgroundStore.runUntilTask(taskId)"
        :disabled="!playgroundStore.readyToStart"
    >
        <el-icon><Play /></el-icon>
        <span>{{ $t('playground.run_task') }}</span>
        <template #dropdown>
            <el-dropdown-menu>
                <el-dropdown-item :icon="Play" @click="playgroundStore.runUntilTask(taskId)">
                    {{ $t('playground.run_this_task') }}
                </el-dropdown-item>
                <el-dropdown-item :icon="PlayBoxMultiple" @click="playgroundStore.runUntilTask(taskId, true)">
                    {{ $t('playground.run_task_and_downstream') }}
                </el-dropdown-item>
            </el-dropdown-menu>
        </template>
    </el-dropdown>
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
