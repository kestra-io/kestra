<template>
    <component
        :is="disabled ? 'span' : 'a'"
        class="ks-sidebar-item"
        :class="{'is-active': active, 'is-locked': locked, 'is-disabled': disabled}"
        :href="disabled ? undefined : (href || undefined)"
        :aria-current="active ? 'page' : undefined"
        :aria-disabled="disabled ? 'true' : undefined"
        @click="onClick"
    >
        <component v-if="icon" :is="icon" :size="16" class="ks-sidebar-item__icon" />
        <span class="ks-sidebar-item__title">
            <slot>{{ title }}</slot>
        </span>
        <slot name="suffix" />
        <LockOutline v-if="locked" :size="12" class="ks-sidebar-item__lock" />
    </component>
</template>

<script setup lang="ts">
    import type {Component} from "vue"
    import LockOutline from "vue-material-design-icons/LockOutline.vue"

    const props = defineProps<{
        title: string
        icon?: Component
        href?: string
        active?: boolean
        locked?: boolean
        disabled?: boolean
    }>()

    const emit = defineEmits<{
        click: [evt: MouseEvent]
    }>()

    function onClick(evt: MouseEvent) {
        if (props.disabled) {
            evt.preventDefault()
            return
        }
        emit("click", evt)
    }

    defineSlots<{
        default?(): unknown
        suffix?(): unknown
    }>()
</script>

<style scoped lang="scss">
.ks-sidebar-item {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    padding: 4px var(--ks-spacing-2);
    margin-bottom: var(--ks-spacing-1);
    min-height: 26px;
    border-radius: var(--ks-radius-base);
    text-decoration: none;
    color: var(--ks-text-secondary);
    font-size: var(--ks-font-size-xs);
    font-weight: var(--ks-font-weight-medium);
    transition: background-color 0.15s ease, color 0.15s ease;
    cursor: pointer;

    &:hover {
        background-color: var(--ks-bg-hover);
        color: var(--ks-text-secondary);
    }

    &.is-active {
        background-color: var(--ks-bg-active);
        color: var(--ks-text-link);
    }

    &.is-locked,
    &.is-locked:hover,
    &.is-active.is-locked,
    &.is-active.is-locked:hover {
        color: var(--ks-text-inactive);
    }

    &.is-disabled {
        opacity: 0.5;
        cursor: not-allowed;

        &:hover {
            background-color: transparent;
        }
    }
}

.ks-sidebar-item__icon {
    display: inline-flex;
    align-items: center;
    flex: 0 0 auto;
    color: inherit;
}

.ks-sidebar-item__title {
    flex: 1 1 auto;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.ks-sidebar-item__lock {
    flex: 0 0 auto;
    opacity: 0.5;
    color: inherit;
    margin-left: var(--ks-spacing-1);
}
</style>
