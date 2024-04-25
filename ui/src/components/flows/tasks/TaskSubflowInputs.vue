<template>
    <div
        class="d-flex w-100"
        v-for="inputWithValue in Object.entries(inputsWithValue)"
        :key="inputWithValue[0]"
    >
        <el-select
            class="flex-fill flex-grow-1 w-100 me-2"
            :model-value="inputWithValue[0]"
            @update:model-value="onSelectedInputChange(inputWithValue[0], $event)"
            filterable
            :persistent="false"
            :placeholder="task.namespace && task.flowId ? 'Select' : 'Select namespace and flowId first'"
            :disabled="!task.namespace || !task.flowId"
        >
            <el-option
                v-for="item in filteredInputs(inputWithValue[0])"
                :key="item"
                :label="item"
                :value="item"
            />
        </el-select>
        <task-expression
            class="flex-fill flex-grow-1 w-100 me-2"
            :model-value="inputWithValue[1]"
            :task="task"
            @update:model-value="onInputValueChange(inputWithValue[0], $event)"
            :schema="schema"
            :definitions="definitions"
        />
        <div class="flex-shrink-1">
            <el-button-group class="d-flex flex-nowrap">
                <el-button :icon="Plus" @click="addItem" />
                <el-button :icon="Minus" @click="removeItem(inputWithValue[0])" />
            </el-button-group>
        </div>
    </div>
</template>
<script>
    import Task from "./Task";
    import Plus from "vue-material-design-icons/Plus.vue";
    import Minus from "vue-material-design-icons/Minus.vue";
    import TaskExpression from "./TaskExpression.vue";
    import axios from "axios";

    export default {
        components: {TaskExpression},
        computed: {
            Minus() {
                return Minus
            },
            Plus() {
                return Plus
            }
        },
        mixins: [Task],
        data() {
            return {
                subflowInputs: undefined,
                inputsWithValue: this.modelValue ?? this.emptyValueProvider()
            }
        },
        emits: ["update:modelValue"],
        methods: {
            emptyValueProvider() {
                return {"": undefined};
            },
            async fetchInputKeys(namespace, flowId, revision) {
                try {
                    return (await this.$store.dispatch(
                        "flow/loadFlow",
                        {
                            namespace: namespace,
                            id: flowId,
                            revision: revision,
                            source: false,
                            store: false,
                            httpClient: axios
                        }
                    )).inputs?.map(input => input.id) ?? [];
                } catch(e) {
                    this.$store.dispatch("core/showMessage", {
                        variant: "error",
                        title: this.$t("error"),
                        message: e.message,
                    })
                    return [];
                }
            },
            filteredInputs(toKeep) {
                const selectedInputs = Object.keys(this.inputsWithValue);
                return this.subflowInputs.filter(input => input === toKeep || !selectedInputs.includes(input));
            },
            onSelectedInputChange(keyToMove, newKey) {
                this.inputsWithValue[newKey] = this.inputsWithValue[keyToMove];
                delete this.inputsWithValue[keyToMove];
            },
            addItem() {
                this.inputsWithValue[""] = undefined;
            },
            removeItem(inputId) {
                if (Object.entries(this.inputsWithValue).length === 1) {
                    this.inputsWithValue = this.emptyValueProvider();
                    return;
                }
                delete this.inputsWithValue[inputId];
            },
            onInputValueChange(key, value) {
                this.inputsWithValue[key] = value;
            }
        },
        async created() {
            if (this.task.namespace && this.task.flowId) {
                this.subflowInputs = await this.fetchInputKeys(this.task.namespace, this.task.flowId, this.task.revision);
            }
        },
        watch: {
            inputsWithValue: {
                deep: true,
                handler() {
                    this.$emit("update:modelValue", this.inputsWithValue);
                }
            },
            "task.namespace": {
                async handler(newNamespace) {
                    if (this.task.flowId && newNamespace) {
                        this.subflowInputs = await this.fetchInputKeys(this.task.namespace, this.task.flowId, this.task.revision);
                    }
                }
            },
            "task.flowId": {
                async handler(newFlowId) {
                    if (this.task.namespace && newFlowId) {
                        this.subflowInputs = await this.fetchInputKeys(this.task.namespace, this.task.flowId, this.task.revision);
                    }
                }
            },
            "task.revision": {
                async handler() {
                    if (this.task.flowId && this.task.namespace) {
                        this.subflowInputs = await this.fetchInputKeys(this.task.namespace, this.task.flowId, this.task.revision);
                    }
                }
            }
        }
    };
</script>
