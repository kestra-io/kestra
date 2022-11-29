<template>
    <span v-if="labels">
        <el-tag
            v-for="(value, key) in labels"
            :key="key"
            class="mr-1"
            type="info"
            size="small"
            disable-transitions
        >
            <router-link :to="link(key, value)">
                <span class="text-primary">{{ key }}:</span> {{ value }}
            </router-link>
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
        },
        methods: {
            link(key, value) {
                const labels = new Map();
                (this.$route.query.labels !== undefined ?
                    (typeof(this.$route.query.labels) === "string" ? [this.$route.query.labels] : this.$route.query.labels)  :
                    []
                )
                    .forEach(label => {
                        const split = label.split(":");

                        labels[split[0]] = split[1];
                    })
                labels[key] = value;

                const qs = {
                    ...this.$route.query,
                    ...{"labels": Object.entries(labels).map((label) => label[0] + ":" + label[1])}
                };

                delete qs.page;

                return {name: this.$route.name, query: qs}
            }
        }
    };
</script>

<style lang="scss" scoped>
@import "../../styles/_variable.scss";
:deep(.badge) {
    font-size: $font-size-xs;
}
</style>
