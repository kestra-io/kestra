<template>
    <div class="d-flex align-items-center w-100 item">
        <div v-if="item.name" class="col-3 text-truncate">
            <span class="small">{{ item.name }}</span>
        </div>

        <div class="col flex-grow-1 overflow-auto text-nowrap">
            <div class="me-3 overflow-x-auto scroller">
                <el-tag
                    v-for="value in item.value"
                    :key="value"
                    type="info"
                    class="me-2"
                >
                    <span class="small">{{ value }}</span>
                </el-tag>
            </div>
        </div>

        <div class="col-auto">
            <slot name="delete" />
        </div>
    </div>
</template>

<script setup lang="ts">
    defineProps({item: {type: Object, required: true}});
</script>

<style lang="scss" scoped>
.item {
    .small {
        font-size: var(--el-font-size-extra-small);
    }
}
</style>
