<template>
    <b-button
        @click="click"
        class="node-action"
    >
        <axis-y-arrow :title="$t('link to sub flow')" />
    </b-button>
</template>
<script>
    import AxisYArrow from "vue-material-design-icons/AxisYArrow";

    export default {
        components: {
            AxisYArrow
        },
        props: {
            executionId: {
                type :String,
                default: undefined
            },
            namespace: {
                type :String,
                default: undefined
            },
            flowId: {
                type :String,
                default: undefined
            },
            tabFlow: {
                type :String,
                default: "overview"
            },
            tabExecution: {
                type :String,
                default: "topology"
            }
        },
        methods: {
            click() {
                if (this.executionId && this.namespace && this.flowId) {
                    this.$router.push({
                        name: this.routeName,
                        params: {namespace: this.namespace, flowId: this.flowId, id: this.executionId},
                        query: this.query
                    });
                } else if (this.executionId) {
                    this.$store
                        .dispatch("execution/loadExecution", {id: this.executionId})
                        .then(value => {
                            this.$store.commit("execution/setExecution", value);
                            this.$router.push({name: this.routeName, params: this.params(value), query: this.query})
                        })
                } else {
                    this.$router.push({name: this.routeName, params: this.params(), query: this.query})
                }
            },
            params (execution) {
                if (execution) {
                    return {namespace: execution.namespace, flowId: execution.flowId, id: execution.id}
                } else {
                    return {namespace: this.namespace, id: this.flowId}
                }
            },
        },
        computed : {
            routeName () {
                return this.executionId ? "executions/update" : "flows/update"
            },
            query () {
                return this.executionId ? {tab: this.tabExecution} : {tab: this.tabFlow}
            }
        }
    }
</script>