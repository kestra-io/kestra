<template>
    <div class="d-flex w-100" v-for="(item, index) in currentValue" :key="index">
        <div class="flex-fill flex-grow-1 w-100 me-2">
            <el-input
                :model-value="item[0]"
                @update:model-value="onKey(index, $event)"
                @change="onKeyChange(index, $event)"
            />
        </div>
        <div class="flex-fill flex-grow-1 w-100 me-2">
            <component
                :is="`task-${schema.additionalProperties ? getType(schema.additionalProperties) : 'expression'}`"
                :model-value="item[1]"
                @update:model-value="onValueChange(index, $event)"
                :root="getKey(item[0])"
                :schema="schema.additionalProperties"
                :required="isRequired(item[0])"
                :definitions="definitions"
            />
        </div>
        <div class="flex-shrink-1">
            <el-button-group class="d-flex flex-nowrap">
                <el-button :icon="Plus" @click="addItem" />
                <el-button :icon="Minus" @click="removeItem(index)" />
            </el-button-group>
        </div>
    </div>
</template>

<script setup>
    import Plus from "vue-material-design-icons/Plus.vue";
    import Minus from "vue-material-design-icons/Minus.vue";
</script>

<script>
    import {toRaw} from "vue";
    import Task from "./Task";

    function emptyValueObjectProvider() {
        return {"": undefined};
    }

    function emptyValueEntriesProvider() {
        return ["", undefined];
    }

    export default {
        mixins: [Task],
        emits: ["update:modelValue"],
        data() {
            return {
                currentValue: undefined,
            };
        },
        created() {
            this.currentValue = Object.entries(toRaw(this.values));
        },
        computed: {
            values() {
                if (this.modelValue === undefined) {
                    return emptyValueObjectProvider();
                }

                return this.modelValue;
            },
        },
        watch: {
            modelValue(_newValue, _oldValue) {
                this.currentValue = Object.entries(toRaw(this.values));
            }
        },
        methods:{
            emitLocal(index, value) {
                const local = this.currentValue
                    .reduce(function(acc, cur, i) {
                        acc[i === index ? value : cur[0]] = cur[1];
                        return acc;
                    }, {});

                this.$emit("update:modelValue", local);
            },
            onValueChange(key, value) {
                const local = this.currentValue || [];
                local[key][1] = value;
                this.currentValue = local;

                this.emitLocal();
            },
            onKey(key, value) {
                const local = this.currentValue || [];
                local[key][0] = value;
                this.currentValue = local;
            },
            onKeyChange(index, value) {
                this.emitLocal(index, value);
            },
            addItem() {
                const local = this.currentValue || [];
                local.push(["", undefined]);

                this.currentValue = local;

                this.emitLocal();
            },
            removeItem(x) {
                let local = this.currentValue || [];
                if (local.length === 1) {
                    local = [emptyValueEntriesProvider()];
                } else {
                    local.splice(x, 1);
                }

                this.currentValue = local;

                this.emitLocal();
            },
        },
    };
</script>
