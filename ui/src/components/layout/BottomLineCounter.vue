<template>
    <li class="left">
        <p>
            <span
                class="counter"
                v-html="$t('selection.selected', {count: modelValue ? total : selections.length})">
            </span>

            <el-link
                type="info"
                v-if="selections.length<total && !modelValue"
                @click="all()"
            >
                {{ $t('selection.all', {count: total}) }}
            </el-link>
        </p>
    </li>
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
    span.counter {
        border-left: 6px solid var(--el-color-warning);
        padding-left: 0.5rem;
        margin-right: 5px;
    }
</style>
