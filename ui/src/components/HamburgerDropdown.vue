<template>
    <KsDropdown>
        <KsButton>
            <DotsVertical />
        </KsButton>
        <template #dropdown>
            <KsDropdownMenu>
                <slot />
            </KsDropdownMenu>
        </template>
    </KsDropdown>
</template>

<script setup lang="ts">
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue"
</script>

