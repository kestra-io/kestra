<template>
    <el-dropdown>
        <el-button>
            <DotsVertical />
        </el-button>
        <template #dropdown>
            <el-dropdown-menu>
                <slot />
            </el-dropdown-menu>
        </template>
    </el-dropdown>
</template>
<script>
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue";

    export default {
        components: {DotsVertical},
        props:{

        },
    }
</script>

