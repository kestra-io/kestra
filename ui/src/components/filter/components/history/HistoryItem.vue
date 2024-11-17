<template>
    <div class="d-flex align-items-center w-100 item">
        <div v-if="item.name" class="col-3 text-truncate">
            <span class="small">{{ item.name }}</span>
        </div>

        <div class="col flex-grow-1 overflow-auto text-nowrap">
            <div class="me-3 overflow-x-auto scroller">
                <el-tag v-for="value in item.value" :key="value" class="me-2">
                    <span class="small">{{ formatLabel(value) }}</span>
                </el-tag>
            </div>
        </div>

        <div class="col-auto">
            <slot name="delete" />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {formatLabel} from "../../useFilters.js";

    defineProps({item: {type: Object, required: true}});
</script>

<style lang="scss" scoped>
.item {
    .el-tag {
        background: var(--bs-border-color);
        color: var(--bs-gray-900);
    }

    .small {
        font-size: var(--el-font-size-extra-small);
    }
}
</style>
