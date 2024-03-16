<template>
    <el-tooltip
        placement="bottom"
        trigger="hover"
        :persistent="false"
        :show-after="750"
        :hide-after="0"
    >
        <template #content>
            <div v-for="(queryPart) in getReadableQuery()" :key="queryPart">
                {{ queryPart }}
            </div>
        </template>
        <el-tag
            :type="isSelected() ? 'primary' : 'info'"
            size="large"
            @click="onClick"
            @close="showConfirmDialog()"
            closable
            class="me-1"
            disable-transitions
        >
            <router-link :to="searchLink">
                {{ label }}
            </router-link>
        </el-tag>
    </el-tooltip>
</template>

<script>
    import _isEqual from "lodash/isEqual";

    export default {
        emits: [
            "clicked",
            "deleted"
        ],
        props: {
            query: {
                type: Object,
                required: true
            },
            label: {
                type: String,
                required: true
            }
        },
        computed: {
            searchLink() {
                return {
                    name: this.$route.name,
                    params: this.$route.params,
                    query: this.query
                };
            }
        },
        methods: {
            showConfirmDialog() {
                this.$toast().confirm(
                    this.$t("delete confirm", {name: this.label}),
                    this.onDelete,
                    () => {
                    }
                );
            },
            onClick() {
                this.$emit("clicked");
            },
            onDelete() {
                this.$emit("deleted", this.label);
            },
            isSelected() {
                return _isEqual(this.query, this.$route.query);
            },
            getReadableQuery() {
                return Object.entries(this.query)
                    .map(([key, value]) => `${key}: ${value}`);
            }
        }
    }
</script>

<style lang="scss" scoped>
    .el-tag {
        & a, span, :deep(.el-icon) {
            color: var(--bs-white);
        }

        &.el-tag--info {
            background: var(--bs-gray-600);
        }
    }
</style>