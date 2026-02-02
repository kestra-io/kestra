<template>
    <el-tooltip
        v-if="tooltip"
        effect="light"
        :content="tooltip"
        :rawContent="true"
        v-bind="placement ? {placement} : {}"
        :persistent="false"
        :enterable="false"
        transition=""
        :hideAfter="0"
    >
        <el-button
            v-bind="buttonAttrs"
            class="ks-icon-button"
            :disabled="disabled"
            :aria-label="ariaLabel || tooltip"
            :tag="buttonTag"
            :to="disabled ? undefined : to"
            :replace="replace"
            :nativeType="nativeType"
        >
            <slot />
        </el-button>
    </el-tooltip>
    <el-button
        v-else
        v-bind="buttonAttrs"
        class="ks-icon-button"
        :disabled="disabled"
        :aria-label="ariaLabel"
        :tag="buttonTag"
        :to="disabled ? undefined : to"
        :replace="replace"
        :nativeType="nativeType"
    >
        <slot />
    </el-button>
</template>

<script setup lang="ts">
    import {computed, useAttrs} from "vue";
    import {type RouteLocationRaw} from "vue-router";

    defineOptions({inheritAttrs: false});

    const props = withDefaults(defineProps<{
        tooltip?: string;
        placement?: string;
        ariaLabel?: string;
        disabled?: boolean;
        to?: RouteLocationRaw;
        replace?: boolean;
    }>(), {
        tooltip: "",
        placement: "left",
        ariaLabel: "",
        disabled: false,
        to: undefined,
        replace: false,
    });

    const attrs = useAttrs();
    const buttonAttrs = computed(() => ({
        ...attrs,
        class: [attrs.class],
    }));

    const buttonTag = computed(() => (props.to ? "router-link" : undefined));
    const nativeType = computed(() => (props.to ? undefined : "button"));
</script>
