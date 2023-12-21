<template>
    <component :icon="AxisYArrow" :is="component" @click="click" class="node-action" size="small">
        <span v-if="component !== 'el-button'">{{ $t('sub flow') }}</span>
    </component>
</template>

<script setup>
    import AxisYArrow from "vue-material-design-icons/AxisYArrow.vue";
</script>

<script>
    export default {
        props: {
            component: {
                type: String,
                default: "el-button"
            },
            executionId: {
                type: String,
                default: undefined
            },
            namespace: {
                type: String,
                default: undefined
            },
            flowId: {
                type: String,
                default: undefined
            },
            tabFlow: {
                type: String,
                default: "overview"
            },
            tabExecution: {
                type: String,
                default: "topology"
            }
        },
        methods: {
            click() {
                if (this.executionId && this.namespace && this.flowId) {
                    this.$router.push({
                        name: this.routeName,
                        params: {
                            namespace: this.namespace,
                            flowId: this.flowId,
                            id: this.executionId,
                            tab: this.tab,
                            tenant: this.$route.params.tenant
                        },
                    });
                } else if (this.executionId) {
                    this.$store
                        .dispatch("execution/loadExecution", {id: this.executionId})
                        .then(value => {
                            this.$store.commit("execution/setExecution", value);
                            this.$router.push({name: this.routeName, params: this.params(value)})
                        })
                } else {
                    this.$router.push({name: this.routeName, params: this.params()})
                }
            },
            params (execution) {
                if (execution) {
                    return {namespace: execution.namespace, flowId: execution.flowId, id: execution.id, tab: this.tab}
                } else {
                    return {namespace: this.namespace, id: this.flowId, tab: this.tab}
                }
            },
        },
        computed : {
            routeName () {
                return this.executionId ? "executions/update" : "flows/update"
            },
            tab () {
                return this.executionId ? this.tabExecution : this.tabFlow
            }
        }
    }
</script>