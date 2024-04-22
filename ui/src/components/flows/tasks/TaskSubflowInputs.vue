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
                return (await this.$store.dispatch(
                    "flow/loadFlow",
                    {
                        namespace: namespace,
                        id: flowId,
                        revision: revision,
                        source: false,
                        store: false
                    }
                )).inputs?.map(input => input.id) ?? [];
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
                if (this.inputsWithValue.length === 1) {
                    this.inputsWithValue = this.emptyValue;
                    return;
                }
                delete this.inputsWithValue[inputId];
            },
            onInputValueChange(key, value) {
                this.inputsWithValue[key] = value;
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
                immediate: true,
                async handler() {
                    if (this.task.flowId) {
                        this.subflowInputs = await this.fetchInputKeys(this.task.namespace, this.task.flowId, this.task.revision);
                    }
                }
            },
            "task.flowId": {
                immediate: true,
                async handler() {
                    if (this.task.namespace) {
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
