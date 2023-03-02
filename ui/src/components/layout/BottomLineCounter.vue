<template>
    <el-button-group>
        <el-button size="large" @click="all()">
            <span v-html="$t('selection.selected', {count: modelValue ? total : selections.length})" />

            <span class="counter" v-if="selections.length<total && !modelValue" v-html="$t('selection.all', {count: total})" />
        </el-button>
        <slot />
    </el-button-group>
</template>
<script>
    export default {
        props: {
            total: {type: Number, required: true},
            selections: {type: Array, required: true},
            modelValue: {type: Boolean, required: true},
        },
        emits: ["update:modelValue"],
        methods: {
            all() {
                this.$emit("update:modelValue", true);
            }
        }
    }
</script>
<style lang="scss" scoped>
    span {
        color: var(--bs-tertiary);
    }

    span.counter {
        border-left: 1px solid var(--bs-gray-100-darken-5);
        padding-left: 0.5rem;
        margin-left: 5px;
        margin-right: 5px;
    }
</style>
