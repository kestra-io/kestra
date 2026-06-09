<template>
    <KsDropdown trigger="click" placement="bottom-end" :persistent="true">
        <KsButton link>
            <KsIcon><DotsVertical /></KsIcon>
            <span class="d-none d-lg-inline-block">{{ t("actions") }}</span>
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
    import {useI18n} from "vue-i18n"
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue"
    import {asItemKey} from "../layout/navBarActionsContext"

    interface Action {
        component: Component;
        props?: Record<string, unknown>;
        on?: Record<string, unknown>;
    }

    defineProps<{
        actions: Action[];
        execution: Record<string, unknown>;
    }>()

    const {t} = useI18n({useScope: "global"})

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
