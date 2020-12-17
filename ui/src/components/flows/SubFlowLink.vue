<template>
    <div>
        <slot v-if="useSlot" />
        <router-link v-else :to="{name:this.routeName, params: this.params, query: this.query}">
            <b-button
                class="node-action"
                size="sm"
                :title="$t('link to sub flow')"
            >
                <axis-y-arrow title />
            </b-button>
        </router-link>
    </div>
</template>
<script>
    import AxisYArrow from "vue-material-design-icons/AxisYArrow";

    export default {
        components: {
            AxisYArrow
        },
        props: {
            useSlot: {
                type :Boolean,
                default: false
            },
            namespace: {
                type :String,
                required: true
            },
            executionId: {
                type :String,
                default: undefined
            },
            flowId: {
                type :String,
                required: true
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
        computed : {
            routeName () {
                return this.executionId ? "executionEdit" : "flowEdit"
            },
            params () {
                if (this.executionId) {
                    return {namespace: this.namespace, flowId: this.flowId, id: this.executionId}
                } else {
                    return {namespace: this.namespace, id: this.flowId}
                }
            },
            query () {
                return this.executionId ? {tab: this.tabExecution} : {tab: this.tabFlow}
            }
        }
    }
</script>