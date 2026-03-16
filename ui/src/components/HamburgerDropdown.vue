<template>
    <ks-dropdown>
        <ks-button>
            <DotsVertical />
        </ks-button>
        <template #dropdown>
            <ks-dropdown-menu>
                <slot />
            </ks-dropdown-menu>
        </template>
    </ks-dropdown>
</template>

<script setup lang="ts">
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue";
</script>

