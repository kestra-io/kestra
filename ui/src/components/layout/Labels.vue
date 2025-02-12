<template>
    <span data-component="FILENAME_PLACEHOLDER" v-if="labels">
        <!-- 'el-check-tag' would be a better fit but it currently lacks customization (missing size, bold font) -->
        <template
            v-for="(value, key) in labelMap"
            :key="key"
        >
            <router-link v-if="filterEnabled" :to="link(key, value)" class="me-1 labels el-tag el-tag--small" :class="{'el-tag--primary': checked(key, value)}">
                {{ key }}: {{ value }}
            </router-link>
            <div v-else class="me-1 labels el-tag el-tag--small" :class="{'el-tag--primary': checked(key, value)}">{{ key }}: {{ value }}</div>
        </template>
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
        // this is needed as flows uses a Map and Execution a List of Labels.
        // if we align both of them this can be removed
        computed: {
            labelMap() {
                if (Array.isArray(this.labels)) {
                    return Object.fromEntries(this.labels.map(label => [label.key, label.value]));
                } else {
                    return this.labels;
                }
            },
            labelsFromQuery() {
                const labels = new Map();
                (this.$route.query.labels !== undefined ?
                    (typeof(this.$route.query.labels) === "string" ? [this.$route.query.labels] : this.$route.query.labels)  :
                    []
                )
                    .forEach(label => {
                        const separatorIndex = label.indexOf(":");

                        if (separatorIndex === -1) {
                            return;
                        }

                        labels.set(label.slice(0, separatorIndex), label.slice(separatorIndex + 1));
                    })

                return labels;
            }
        },
        methods: {
            checked(key, value) {
                return this.labelsFromQuery.has(key) && this.labelsFromQuery.get(key) === value;
            },
            link(key, value) {
                const labels = this.labelsFromQuery;

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
