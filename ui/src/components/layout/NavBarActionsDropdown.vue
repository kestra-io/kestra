<template>
    <el-dropdown trigger="click">
        <el-button :icon="DotsVertical" />
        <template #dropdown>
            <el-dropdown-menu>
                <slot />
            </el-dropdown-menu>
        </template>
    </el-dropdown>
</template>

<script setup lang="ts">
    import {provide} from "vue";
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue";
    import {asItemKey} from "./navBarActionsContext";

    provide(asItemKey, true);
</script>
