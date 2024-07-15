<template>
    <div class="d-flex w-100 task-array" v-for="(item, index) in values" :key="'array-' + index">
        <div class="flex-fill flex-grow-1 w-100 me-2">
            <component
                :is="`task-${getType(schema.items)}`"
                :model-value="item"
                @update:model-value="onInput(index, $event)"
                :root="getKey(index)"
                :schema="schema.items"
                :definitions="definitions"
            />
        </div>
        <div class="flex-shrink-1">
            <el-button-group class="d-flex flex-nowrap">
                <el-button :icon="Plus" @click="addItem" />
                <el-button :icon="Minus" @click="removeItem(index)" :disabled="index === 0 && values.length === 1" />
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

    export default {
        mixins: [Task],
        emits: ["update:modelValue"],
        created() {
            if (!Array.isArray(this.modelValue)) {
                this.$emit("update:modelValue", []);
            }
        },
        computed: {
            values() {
                if (this.modelValue === undefined || (Array.isArray(this.modelValue) && this.modelValue.length === 0)) {
                    return this.schema.default || [undefined];
                }

                return this.modelValue;
            },
        },
        methods: {
            getPropertiesValue(properties) {
                return this.modelValue && this.modelValue[properties]
                    ? this.modelValue[properties]
                    : undefined;
            },
            onInput(index, value) {
                const local = this.modelValue || [];
                local[index] = value;

                this.$emit("update:modelValue", local);
            },
            addItem() {
                let local = this.modelValue || [];
                local.push(undefined);

                // click on + when there is no items
                if (this.modelValue === undefined) {
                    local.push(undefined);
                }

                this.$emit("update:modelValue", local);
            },
            removeItem(x) {
                let local = this.modelValue || [];
                local.splice(x, 1);

                if (local.length === 1) {
                    let raw = toRaw(local[0]);

                    if (raw === null || raw === undefined) {
                        local = undefined;
                    }
                }

                this.$emit("update:modelValue", local);
            },
        },
    };
</script>
<style lang="scss" scoped>
    .task-array {
        margin-bottom: 2px;
    }

</style>
