<template>
    <el-tooltip :disabled="tooltip === undefined" :content="tooltip" effect="light">
        <el-select
            data-test-id="time-selector"
            :model-value="value"
            :placeholder="placeholder"
            @change="$emit('change', $event)"
            :clearable="clearable"
        >
            <template #prefix>
                <clock-outline />
            </template>
            <el-option
                v-for="preset in options"
                :key="preset.value"
                :label="$t(preset.label)"
                :value="preset.value"
            />
        </el-select>
    </el-tooltip>
</template>

<script>
    import ClockOutline from "vue-material-design-icons/ClockOutline.vue";

    export default {
        components: {
            ClockOutline
        },
        emits: [
            "change"
        ],
        props: {
            placeholder: {
                type: String,
                default: undefined
            },
            value: {
                type: String,
                default: undefined
            },
            options: {
                type: Array,
                default: () => []
            },
            tooltip: {
                type: String,
                default: undefined
            },
            clearable: {
                type: Boolean,
                default: false
            }
        }
    }
</script>