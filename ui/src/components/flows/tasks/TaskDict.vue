<template>
    <div class="d-flex w-100" v-for="(item, key) in values" :key="key">
        <div class="flex-fill flex-grow-1 w-100 me-2">
            <el-input
                :model-value="key"
                @update:model-value="onKey(key, $event)"
            />
        </div>
        <div class="flex-fill flex-grow-1 w-100 me-2">
            <component
                :is="`task-${getType(schema.additionalProperties)}`"
                :model-value="item"
                @update:model-value="onInput(key, $event)"
                :root="getKey(key)"
                :schema="schema.additionalProperties"
                :required="isRequired(key)"
                :definitions="definitions"
            />
        </div>
        <div class="flex-shrink-1">
            <el-button-group class="d-flex flex-nowrap">
                <el-button :icon="Plus" @click="addItem" />
                <el-button :icon="Minus" @click="removeItem(key)" :disabled="key === 0 && values.length === 1" />
            </el-button-group>
        </div>
    </div>
</template>

<script setup>
    import Plus from "vue-material-design-icons/Plus.vue";
    import Minus from "vue-material-design-icons/Minus.vue";
</script>

<script>
    import Task from "./Task";

    export default {
        mixins: [Task],
        emits: ["update:modelValue"],
        computed: {
            values() {
                if (this.modelValue === undefined) {
                    return {"": undefined};
                }

                return this.modelValue;
            },
        },
        methods:{
            onInput(key, value) {
                const local = this.modelValue || {};
                local[key] = value;

                this.$emit("update:modelValue", local);
            },
            onKey(key, value) {
                const local = this.modelValue || {};
                const dictValue = local[key];
                delete local[key];

                local[value] = dictValue;

                this.$emit("update:modelValue", local);
            },
            addItem() {
                const local = this.modelValue || {};
                local[undefined] = undefined;

                this.$emit("update:modelValue", local);
            },
            removeItem(key) {
                const local = this.modelValue || {};
                delete local[key];

                this.$emit("update:modelValue", local);
            },
        },
    };
</script>
