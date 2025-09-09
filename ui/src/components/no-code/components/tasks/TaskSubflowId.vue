<template>
    <el-select
        :modelValue="values"
        @update:model-value="onInput"
        filterable
        clearable
        :persistent="false"
        allowCreate
        :placeholder="task.namespace ? 'Select' : 'Select namespace first'"
        :disabled="!task.namespace"
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
    import {mapStores} from "pinia";
    import {useFlowStore} from "../../../../stores/flow";
    import Task from "./MixinTask";

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
                    this.flowIds = (await this.flowStore.flowsByNamespace(this.namespace))
                        .map(flow => flow.id);

                    if (this.namespace === this.flowStore.flow.namespace) {
                        this.flowIds = this.flowIds.filter(id => id !== this.flowStore.flow.id)
                    }
                }
            }
        },
        computed: {
            ...mapStores(useFlowStore),
            namespace() {
                return this.task?.namespace ?? this.flowStore.flow?.namespace;
            },
        }
    };
</script>
