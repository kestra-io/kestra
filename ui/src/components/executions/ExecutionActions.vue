<template>
    <KsDropdown trigger="click" placement="bottom-end" :persistent="true">
        <KsButton link data-onboarding-target="execution-actions-menu">
            <KsIcon><DotsVertical /></KsIcon>
            <span class="d-none d-lg-inline-block">{{ $t("actions") }}</span>
        </KsButton>
        <template #dropdown>
            <KsDropdownMenu>
                <component
                    :is="action.component"
                    v-for="(action, idx) in actions"
                    :key="idx"
                    v-bind="action.props ?? {}"
                    v-on="action.on ?? {}"
                    :execution
                />
            </KsDropdownMenu>
        </template>
    </KsDropdown>
</template>

<script setup lang="ts">
    import {provide, type Component} from "vue"
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue"
    import {asItemKey} from "../layout/navBarActionsContext"
    import type {Execution} from "../../stores/executions"

    interface Action {
        component: Component;
        props?: Record<string, unknown>;
        on?: Record<string, unknown>;
    }

    defineProps<{
        actions: Action[];
        execution: Execution;
    }>()


    provide(asItemKey, true)
</script>

<style lang="scss" scoped>
    :deep(.kel-button.is-link) {
        color: var(--ks-text-secondary);
        margin-right: var(--ks-spacing-2);

        &:hover {
            color: var(--ks-text-primary);
        }
    }
</style>
