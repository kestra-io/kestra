<template>
    <div data-component="FILENAME_PLACEHOLDER" class="bulk-select">
        <el-checkbox
            :model-value="selections.length > 0"
            @change="toggle"
            :indeterminate="partialCheck"
        >
            <span v-html="$t('selection.selected', {count: selectAll ? total : selections.length})" />
        </el-checkbox>
        <el-button-group>
            <el-button
                :type="selectAll ? 'primary' : 'default'"
                @click="toggleAll"
                v-if="selections.length < total"
            >
                <span v-html="$t('selection.all', {count: total})" />
            </el-button>
            <slot />
        </el-button-group>
    </div>
</template>
<script>
    export default {
        props: {
            total: {type: Number, required: true},
            selections: {type: Array, required: true},
            selectAll: {type: Boolean, required: true},
        },
        emits: ["update:selectAll", "unselect"],
        methods: {
            toggle(value) {
                if (!value) {
                    this.$emit("unselect");
                }
            },
            toggleAll() {
                this.$emit("update:selectAll", !this.selectAll);
            }
        },
        computed: {
            partialCheck() {
                return !this.selectAll && this.selections.length < this.total;
            },
        }
    }
</script>

<style lang="scss" scoped>
    .bulk-select {
        height: 100%;
        display: flex;
        align-items: center;

        .el-checkbox {
            height: 100%;

            span {
                padding-left: calc(var(--spacer) * 1.5);
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

<style lang="scss">

.el-checkbox {
    &__inner {
        width: 16px;
        height: 16px;
        border: 2px solid var(--bs-gray-700);
        border-radius: 1.5px;
    }

    &.is-checked {
        &__inner {
            background-color: var(--el-text-color-primary);
            border-color: var(--el-text-color-primary);
        }
    }

    &:hover {
        &__inner {
            border-color: var(--el-text-color-primary);
            transition: 1ms ease-in;
        }
    }
}
</style>
