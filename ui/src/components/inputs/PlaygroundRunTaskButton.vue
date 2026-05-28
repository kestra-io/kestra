<template>
    <KsDropdown
        splitButton
        @visible-change="playgroundStore.dropdownOpened = $event"
        :buttonProps="{class: 'el-button--playground'}"
        @click="playgroundStore.runUntilTask(taskId)"
        :disabled="!playgroundStore.readyToStart"
    >
        <KsIcon><Play /></KsIcon>
        <span>{{ $t('playground.run_task') }}</span>
        <template #dropdown>
            <KsDropdownMenu>
                <KsDropdownItem :icon="Play" @click="playgroundStore.runUntilTask(taskId)">
                    {{ $t('playground.run_this_task') }}
                </KsDropdownItem>
                <KsDropdownItem :icon="PlayBoxMultiple" @click="playgroundStore.runUntilTask(taskId, true)">
                    {{ $t('playground.run_task_and_downstream') }}
                </KsDropdownItem>
            </KsDropdownMenu>
        </template>
    </KsDropdown>
</template>

<script setup lang="ts">
    import {usePlaygroundStore} from "../../stores/playground"
    import Play from "vue-material-design-icons/Play.vue"
    import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple.vue"

    const playgroundStore = usePlaygroundStore()

    defineProps<{
        taskId?: string;
    }>()
</script>

<style scoped lang="scss">
.toggle{
    margin-right: 1rem;
}
</style>
