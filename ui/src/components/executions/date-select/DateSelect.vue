<template>
    <el-tooltip :content="tooltip" effect="light">
        <el-select
            :model-value="value"
            @change="$emit('change', $event)"
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
            value: {
                type: String,
                required: true
            },
            options: {
                type: Array,
                default: () => []
            },
            tooltip: {
                type: String,
                required: true
            }
        }
    }
</script>