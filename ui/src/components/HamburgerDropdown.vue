<template>
    <el-dropdown>
        <ks-button>
            <DotsVertical />
        </ks-button>
        <template #dropdown>
            <el-dropdown-menu>
                <slot />
            </el-dropdown-menu>
        </template>
    </el-dropdown>
</template>

<script setup lang="ts">
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue";
</script>

