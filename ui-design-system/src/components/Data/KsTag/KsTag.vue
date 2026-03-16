<script setup lang="ts">
    import {ElTag, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        type?: "" | "success" | "info" | "warning" | "danger" | "primary"
        size?: "large" | "default" | "small"
        closable?: boolean
        effect?: "dark" | "light" | "plain"
        round?: boolean
        disabled?: boolean
        color?: string
    }>()

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        close: []
    }>()

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-tag
        disable-transitions
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @close="emit('close')"
    >
        <template v-if="$slots.default" #default><slot /></template>
    </el-tag>
</template>

<style lang="scss">
    @use "sass:map";
    @import "element-plus/theme-chalk/src/common/var.scss";
    @import "../../../assets/styles/variables.scss";

    .kel-tag {
        --kel-tag-bg-color: var(--ks-tag-background);
        --kel-tag-text-color: var(--ks-tag-content);
        border: 0;

        a {
            color: var(--ks-tag-content);
        }

        @each $i in ($types) {
            &.kel-tag--#{$i} {
                --kel-tag-text-color: #{darken(map.get($colors, $i), 45%)};
                --kel-tag-bg-color: var(--kel-color-#{$i});
                --kel-tag-hover-color: var(--kel-color-#{$i}-dark-2);
            }
        }

        &.kel-tag--plain {
            border: 1px solid var(--kel-tag-border-color);

            @each $i in ($types) {
                &.kel-tag--#{$i} {
                    --kel-tag-text-color: var(--kel-color-#{$i});
                    --kel-tag-bg-color: #FFFFFF;
                    --kel-tag-hover-color: var(--kel-color-#{$i}-dark-2);
                    --kel-tag-border-color: var(--kel-color-#{$i});

                    html.dark & {
                        --kel-tag-bg-color: #{darken(map.get($colors, $i), 45%)};
                    }
                }
            }
        }
    }
</style>