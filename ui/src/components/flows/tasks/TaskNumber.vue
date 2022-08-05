<template>
    <el-input-number
        :model-value="values"
        @update:model-value="onInput"
        :state="isValid"
        :min="schema.min"
        :max="schema.max"
        :step="schema.step"
        type="number"
    />
</template>

<script>
    import Task from "./Task"
    export default {
        mixins: [Task],
        computed: {
            isValid() {
                if (this.required && this.modelValue === undefined) {
                    return false;
                }

                if (this.modelValue !== undefined) {
                    return !isNaN(this.modelValue)
                }

                return true;
            }
        }
    };
</script>
