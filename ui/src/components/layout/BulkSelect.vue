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
