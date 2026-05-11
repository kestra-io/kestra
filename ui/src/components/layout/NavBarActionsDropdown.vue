<template>
    <KsDropdown trigger="click">
        <KsIconButton :ariaLabel="$t('actions')">
            <DotsVertical />
        </KsIconButton>
        <template #dropdown>
            <KsDropdownMenu>
                <slot />
            </KsDropdownMenu>
        </template>
    </KsDropdown>
</template>

<script setup lang="ts">
    import {provide} from "vue"
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue"
    import {asItemKey} from "./navBarActionsContext"

    provide(asItemKey, true)
</script>
