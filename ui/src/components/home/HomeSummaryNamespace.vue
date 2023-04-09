<template>
    <el-card
        :header="$t('homeDashboard.' + (failed ? 'namespacesErrorExecutions' : 'namespacesExecutions'))"
    >
        <namespace-tree-map
            :data="namespaceData"
        />
    </el-card>
</template>

<script>
    import NamespaceTreeMap from "./NamespaceTreeMap.vue";
    import State from "../../utils/state";

    export default {
        components: {
            NamespaceTreeMap
        },
        props: {
            data: {
                type: Object,
                required: true
            },
            failed: {
                type: Boolean,
                default: false
            },
        },
        computed: {
            namespaceData() {
                return Object.keys(this.data)
                    .flatMap((namespace) => {
                        return this.data[namespace]["*"]
                            .flatMap(date => {
                                return Object.keys(date.executionCounts)
                                    .filter(r => this.failed === false || (this.failed === true && State.isFailed(r)))
                                    .map(current => {
                                        return {
                                            date: date.startDate,
                                            namespace: namespace,
                                            state: current.toLowerCase().capitalize(),
                                            count: date.executionCounts[current]
                                        };
                                    })
                            })
                    });
            },
        }
    };
</script>
