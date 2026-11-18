<template>
    <KsDropdown ref="dropdown" trigger="click" persistent>
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
    import {provide, ref} from "vue"
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue"
    import {asItemKey, closeDropdownKey} from "./navBarActionsContext"

    const dropdown = ref<{handleClose: () => void}>()

    provide(asItemKey, true)
    // Element Plus skips its own dismissal when the click was preventDefault-ed, which is what
    // RouterLink does on every click it handles, so an item holding a link has to close the menu.
    provide(closeDropdownKey, () => dropdown.value?.handleClose())
</script>
