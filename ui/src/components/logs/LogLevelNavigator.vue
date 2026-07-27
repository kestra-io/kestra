<template>
    <div
        class="d-flex kel-select__wrapper space-between gap-2"
        :class="{'is-clickable': filterMode}"
        :style="wrapperStyle(level)"
        :role="filterMode ? 'button' : undefined"
        :tabindex="filterMode ? 0 : undefined"
        @click="filterMode && emit('select')"
        @keydown.enter="filterMode && emit('select')"
        @keydown.space.prevent="filterMode && emit('select')"
    >
        <div class="d-flex align-items-center gap-2">
            <span class="circle" :style="circle(level)" />
            <span>({{ (cursorIdx === undefined ? "" : `${cursorIdx + 1} / `) + totalCount }}) {{ level }}</span>
        </div>
        <div v-if="!filterMode" class="d-flex align-items-end gap-1">
            <ChevronUp class="icon-button" @click="forwardEvent('previous')" />
            <ChevronDown class="icon-button" @click="forwardEvent('next')" />
            <Close v-if="isSelected" class="icon-button" @click="forwardEvent('close')" :style="closeButton(level)" />
        </div>
    </div>
</template>
<script setup lang="ts">
    import {computed} from "vue"
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import Close from "vue-material-design-icons/Close.vue"

    const emit = defineEmits(["previous", "next", "close", "select"])

    const props = defineProps<{
        cursorIdx?: number;
        totalCount: number;
        level: string;
        filterMode?: boolean;
    }>()

    const isSelected = computed(() => props.cursorIdx !== undefined)
    const forwardEvent = (eventName: "previous" | "next" | "close") => emit(eventName)
    const circle = (level: string) => ({backgroundColor: `var(--ks-log-${level.toLowerCase()})`})
    const closeButton = (level: string) => ({color: `var(--ks-log-${level.toLowerCase()})`})
    const wrapperStyle = (level: string) =>
        isSelected.value
            ? {border: `1px solid var(--ks-log-border-${level.toLowerCase()})`}
            : {}
</script>
<style scoped lang="scss">
.kel-select__wrapper {
    cursor: unset;
    font-size: var(--ks-font-size-xs);
    font-weight: 600;

    &:hover {
        box-shadow: 0 0 0 1px var(--ks-border-default) inset;
    }

    &.is-clickable {
        cursor: pointer;

        &:hover {
            background: var(--ks-bg-hover);
        }
    }

    .circle {
        display: inline-block;
        width: 10px;
        height: 10px;
        border-radius: 50%;
    }

    .icon-button {
        cursor: pointer;
        font-size: var(--ks-font-size-md);
    }
}
</style>