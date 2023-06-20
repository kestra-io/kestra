<template>
    <span v-if="labels">
        <!-- 'el-check-tag' would be a better fit but it currently lacks customization -->
        <el-tag
            v-for="(value, key) in labels"
            :key="key"
            :type="checked(key) ? 'info' : ''"
            class="me-1 labels"
            size="small"
            disable-transitions
        >
            <router-link v-if="filterEnabled" :to="link(key, value)">
                {{ key }}: {{ value }}
            </router-link>
            <template v-else>{{ key }}: {{ value }}</template>
        </el-tag>
    </span>
</template>

<script>
    export default {
        props: {
            labels: {
                type: Object,
                default: () => {}
            },
            filterEnabled: {
                type: Boolean,
                default: true
            }
        },
        methods: {
            getLabelsFromQuery() {
                const labels = new Map();
                (this.$route.query.labels !== undefined ?
                    (typeof(this.$route.query.labels) === "string" ? [this.$route.query.labels] : this.$route.query.labels)  :
                    []
                )
                    .forEach(label => {
                        const split = label.split(":");

                        labels.set(split[0], split[1]);
                    })

                return labels;
            },
            checked(key) {
                return this.getLabelsFromQuery().has(key);
            },
            link(key, value) {
                const labels = this.getLabelsFromQuery();

                if (labels.has(key)) {
                    labels.delete(key);
                } else {
                    labels.set(key, value);
                }

                const qs = {
                    ...this.$route.query,
                    ...{"labels": Array.from(labels.keys()).map((key) => key + ":" + labels.get(key))}
                };

                delete qs.page;

                return {name: this.$route.name, params: this.$route.params, query: qs};
            }
        }
    };
</script>

<style lang="scss" scoped>
    :deep(.el-tag) {
        & a, span {
            color: var(--bs-white);
        }

        &.el-tag--info {
            background: var(--bs-primary);
        }
    }
</style>
