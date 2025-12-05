<template>
    <el-row v-for="(row, rIdx) in props.rows" :key="rIdx">
        <el-col :span="14" class="label">
            <component :is="row.icon" />
            <el-text truncated>
                {{ row.label }}
            </el-text>
        </el-col>

        <el-col v-if="row.value" :span="10" class="value">
            <el-text truncated>
                <router-link v-if="row.to" :to="row.to">
                    {{ row.value }}
                </router-link>

                <template v-else>
                    {{ row.value }}
                </template>
            </el-text>
        </el-col>

        <el-col v-if="$slots.action" :span="10">
            <slot name="action" />
        </el-col>
    </el-row>
</template>

<script setup lang="ts">
    import type {Component} from "vue";

    import {RouteLocationRaw} from "vue-router";

    const props = defineProps<{
        rows: {
            icon: Component;
            label: string;
            value?: string | number;
            to?: RouteLocationRaw;
        }[];
    }>();
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

.el-row:not(:last-child) {
    margin-bottom: calc($spacer / 2);
}

.el-row {
    & :deep(.el-text),
    & :deep(.el-button) {
        font-size: $font-size-sm;
    }

    & :deep(.label) {
        display: flex;
        align-items: center;

        & span.material-design-icon {
            margin-right: calc($spacer / 2);
        }

        & .el-text {
            color: var(--ks-content-secondary);
        }
    }

    & :deep(.value) {
        display: flex;
        align-items: center;

        & .el-text {
            width: 100%;
            text-align: right;
        }
    }
}
</style>
