<template>
    <el-input-number
        :modelValue="val"
        @update:model-value="onInput"
        :state="isValid"
        :min="schema.minimum"
        :max="schema.maximum"
        :step="schema.step"
        type="number"
        class="w-100"
    />
</template>

<script>
    import Task from "./MixinTask"
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
            },
            val(){
                return this.values ? parseInt(this.values.toString(), 10) : undefined;
            }
        }
    };
</script>
