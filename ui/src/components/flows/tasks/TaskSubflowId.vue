<template>
    <el-select
        :model-value="values"
        @update:model-value="onInput"
        filterable
        clearable
        :persistent="false"
        allow-create
    >
        <el-option
            v-for="item in flowIds"
            :key="item"
            :label="item"
            :value="item"
        />
    </el-select>
</template>
<script>
    import Task from "./Task";
    import {mapState} from "vuex";

    export default {
        mixins: [Task],
        data() {
            return {
                flowIds: []
            }
        },
        watch: {
            namespace: {
                immediate: true,
                async handler() {
                    this.flowIds = (await this.$store.dispatch("flow/flowsByNamespace", this.namespace))
                        .map(flow => flow.id);

                    if (this.namespace === this.flow.namespace) {
                        this.flowIds = this.flowIds.filter(id => id !== this.flow.id)
                    }
                }
            }
        },
        computed: {
            ...mapState("flow", ["flow"]),
            namespace() {
                return this.task?.namespace ?? this.flow?.namespace;
            },
        }
    };
</script>
