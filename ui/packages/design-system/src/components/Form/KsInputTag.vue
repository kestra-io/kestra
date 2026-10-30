<template>
    <ElInputTag
        v-model="model"
        class="ks-input-tag"
        v-bind="({...filteredProps(), ...$attrs} as InputTagProps)"
    >
        <template #tag="{value}">
            <span v-if="invalid?.(value)" class="ks-input-tag-invalid" :title="invalidLabel">
                <KsIcon><AlertBoxOutline /></KsIcon>
                {{ value }}
                <span v-if="invalidLabel" class="ks-input-tag-sr">({{ invalidLabel }})</span>
            </span>
            <span v-else>{{ value }}</span>
        </template>
    </ElInputTag>
</template>

<script setup lang="ts">
    import {ElInputTag, type InputTagProps} from "element-plus"
    import AlertBoxOutline from "vue-material-design-icons/AlertBoxOutline.vue"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<string[]>({default: () => []})

    const props = withDefaults(defineProps<{
        placeholder?: string
        /** Splits typed or pasted text into several tags, so a pasted list becomes one tag per item. */
        delimiter?: string | RegExp
        disabled?: boolean
        size?: "large" | "default" | "small"
        /** Marks the tags it returns `true` for with the error tone and an icon; it never rejects them. */
        invalid?: (value: string) => boolean
        /** Says why a tag is marked, as a tooltip and as text for screen readers, so the state is not carried by color alone. */
        invalidLabel?: string
    }>(), {
        placeholder: undefined,
        delimiter: undefined,
        size: undefined,
        invalid: undefined,
        invalidLabel: undefined,
    })

    const filteredProps = useFilteredProps(props, ["invalid", "invalidLabel"])
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/input-tag';
    @use 'element-plus/theme-chalk/src/tag';
</style>

<style>
    .kel-input-tag.ks-input-tag {
        --kel-fill-color-blank: var(--ks-bg-input);
        --kel-border-color: var(--ks-border-strong);
        --kel-border-color-hover: var(--ks-border-strong);
        --kel-input-focus-border-color: var(--ks-border-focus);
        width: 100%;

        & .kel-tag {
            --kel-tag-bg-color: var(--ks-bg-tag);
            --kel-tag-text-color: var(--ks-text-secondary);
            --kel-tag-border-color: transparent;
            --kel-tag-hover-color: var(--ks-text-secondary);
        }

        & .kel-tag:has(.ks-input-tag-invalid) {
            --kel-tag-bg-color: var(--ks-bg-error);
            --kel-tag-text-color: var(--ks-text-error);
            --kel-tag-hover-color: var(--ks-text-error);
        }

        & .ks-input-tag-invalid {
            display: inline-flex;
            align-items: center;
            gap: var(--ks-spacing-1);
        }

        & .ks-input-tag-sr {
            position: absolute;
            width: 1px;
            height: 1px;
            overflow: hidden;
            clip-path: inset(50%);
            white-space: nowrap;
        }
    }
</style>
