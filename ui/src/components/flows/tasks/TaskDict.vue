<template>
    <el-row v-for="(item, index) in currentValue" :key="index" :gutter="10">
        <el-col :span="6">
            <InputText
                :model-value="item[0]"
                @update:model-value="onKey(index, $event)"
                @change="onKeyChange(index, $event)"
            />
        </el-col>
        <el-col :span="16">
            <component
                :is="`task-${schema.additionalProperties ? getType(schema.additionalProperties) : 'expression'}`"
                :model-value="item[1]"
                @update:model-value="onValueChange(index, $event)"
                :root="getKey(item[0])"
                :schema="schema.additionalProperties"
                :required="isRequired(item[0])"
                :definitions="definitions"
            />
        </el-col>
        <el-col :span="2" class="col align-self-center delete">
            <DeleteOutline @click="removeItem(index)" />
        </el-col>
    </el-row>
    <Add v-if="!disabledAdding" @add="addItem()" />
</template>

<script setup>
    import {DeleteOutline} from "../../code/utils/icons";

    import InputText from "../../code/components/inputs/InputText.vue";
    import Add from "../../code/components/Add.vue";
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
        props: {
            class: {type: String, default: undefined},
        },
        data() {
            return {
                currentValue: undefined,
            };
        },
        created() {
            this.currentValue = Object.entries(toRaw(this.values));
        },
        computed: {
            disabledAdding() {
                return !this.currentValue.at(-1)[0] || !this.currentValue.at(-1)[1];
            },
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
            },
        },
        methods: {
            emitLocal(index, value) {
                const local = this.currentValue.reduce(function (acc, cur, i) {
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

<style scoped lang="scss">
@import "../../code/styles/code.scss";
</style>
