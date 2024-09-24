<template>
    <el-col
        :xs="layout.xs"
        :sm="layout.sm"
        :md="layout.md"
        :lg="layout.lg"
        :xl="layout.xl"
        class="column"
    >
        <p v-if="label" class="label">
            <el-popover
                v-if="!!popover"
                :content="popover"
                trigger="hover"
                :width="324"
            >
                <template #reference>
                    <InformationOutline />
                </template>
            </el-popover>
            <span>{{ label }}</span>
        </p>
        <slot />
    </el-col>
</template>

<script setup lang="ts">
    import {computed} from "vue";

    import InformationOutline from "vue-material-design-icons/InformationOutline.vue";

    const props = defineProps({
        overrides: {type: Object, default: () => {}},
        label: {type: String, default: undefined},
        popover: {type: String, default: undefined},
    });

    const layout = computed(() => {
        return {
            xs: props.overrides?.xs || 24,
            sm: props.overrides?.sm || 12,
            md: props.overrides?.md || 12,
            lg: props.overrides?.lg || 8,
            xl: props.overrides?.xl || 6,
        };
    });
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables.scss";

.column {
    margin-bottom: $spacer;

    & p.label {
        margin-bottom: calc($spacer / 3);
        font-size: $font-size-sm;
        font-weight: 500;

        & > span.el-tooltip__trigger {
            cursor: pointer;
            margin-right: calc($spacer / 2);
        }
    }
}
</style>
