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
    import {decodeParams, encodeParams} from "../filter/utils/helpers";
    import {useFilters} from "../filter/composables/useFilters";

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
            filterUtils() {
                return useFilters(this.$route.name);
            },
            currentFilters() {
                return decodeParams(this.$route.path, this.$route.query, this.$props.include, this.filterUtils.OPTIONS);
            },
            labelsFromQuery() {
                const labels = new Map();
                const queryLabels = this.currentFilters.filter(item => item.label === "labels");

                queryLabels.map(item => item.value[0]).forEach(label => {
                    const separatorIndex = label.indexOf(":");

                    if (separatorIndex === -1) {
                        return;
                    }

                    labels.set(label.slice(0, separatorIndex), label.slice(separatorIndex + 1));
                });

                return labels;
            }
        },
        methods: {
            checked(key, value) {
                return this.labelsFromQuery.has(key) && this.labelsFromQuery.get(key) === value;
            },
            removeLabelFromFilters(key, value) {
                return this.currentFilters.filter(item => !(item.label === "labels" && item.value[0] === `${key}:${value}`))
            },
            appendLabelToFilters(key, value) {
                const labelValue = `${key}:${value}`;
                const label = {label: "labels", value: [labelValue], comparator: this.filterUtils.COMPARATORS.EQUALS};

                return this.currentFilters.concat([label]);
            },
            link(key, value) {
                const modifiedFilters = this.labelsFromQuery.has(key) ?
                    this.removeLabelFromFilters(key, value) :
                    this.appendLabelToFilters(key, value);
                const qs =  encodeParams(this.$route.path, modifiedFilters, this.filterUtils.OPTIONS);

                return {name: this.$route.name, params: this.$route.params, query: qs};
            }
        }
    };
</script>
