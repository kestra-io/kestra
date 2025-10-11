<template>
    <div class="bulk-select">
        <el-checkbox
            :modelValue="selections.length > 0"
            @change="toggle"
            :indeterminate="partialCheck"
        >
            <span v-html="$t('selection.selected', {count: selectAll && total !== undefined ? total : selections.length})" />
        </el-checkbox>
        <el-button-group>
            <el-button
                :type="selectAll ? 'primary' : 'default'"
                @click="toggleAll"
                v-if="total !== undefined && selections.length < total"
            >
                <span v-html="$t('selection.all', {count: total})" />
            </el-button>
            <slot />
        </el-button-group>
    </div>
</template>
<script setup lang="ts">
    import {computed} from "vue";

    const props = defineProps<{
        total?: number;
        selections: unknown[];
        selectAll: boolean;
    }>();

    const emit = defineEmits<{
        (e: "update:selectAll", value: boolean): void;
        (e: "unselect"): void;
    }>();

    const partialCheck = computed(() => {
        return !props.selectAll && (props.total === undefined || props.selections.length < (props.total ?? 0));
    });

    function toggle(value: boolean) {
        if (!value) {
            emit("unselect");
        }
    }

    function toggleAll() {
        emit("update:selectAll", !props.selectAll);
    }
</script>

<style scoped lang="scss">
    .bulk-select {
        height: 100%;
        display: flex;
        align-items: center;

        .el-checkbox {
            height: 100%;

            span {
                padding-left: 1.5rem;
            }
        }

        .el-button-group {
            display: flex;
        }

        > * {
            padding: 0 8px;
        }
    }

    span {
        font-weight: bold;
    }
</style>
