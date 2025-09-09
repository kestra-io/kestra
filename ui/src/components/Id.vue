<template>
    <el-tooltip v-if="hasTooltip" transition="" placement="top" effect="light">
        <template #content>
            <code>{{ value }}</code>
        </template>
        <a href="#">
            <code :id="uuid" @click="emit('click')" class="text-nowrap" :class="{'link': hasClickListener}">
                {{ transformValue }}
            </code>
        </a>
    </el-tooltip>
    <a v-else href="#">
        <code :id="uuid" class="text-nowrap" @click="onClick">
            {{ transformValue }}
        </code>
    </a>
</template>

<script setup lang="ts">
    import {computed, useAttrs} from "vue";
    import Utils from "../utils/utils";

    const props = defineProps({
        value: {
            type: String,
            default: undefined
        },
        shrink: {
            type: Boolean,
            default: true
        },
        size: {
            type: Number,
            default: 8
        }
    })

    const uuid = Utils.uid();

    const emit = defineEmits(["click"]);

    const hasTooltip = computed(() => {
        return props.shrink && props.value && props.value.length > props.size;
    })

    const attrs = useAttrs();

    const hasClickListener = computed(() => {
        return Boolean(attrs.onClick);
    })

    const transformValue = computed(() => {
        if (!props.value) {
            return "";
        }

        if (!props.shrink) {
            return props.value;
        }

        return props.value.toString().substr(0, props.size) +
            (props.value.length > props.size && props.size !== 8 ? "…" : "");
    })
</script>

<style lang="scss" scoped>
a code {
    font-size: 0.875em;
    color: var(--bs-code-color);
    word-wrap: break-word;
}

code.link {
    cursor: pointer;
    &:hover {
        color: rgba(var(--bs-link-color-rgb), var(--bs-link-opacity, 1));
    }
}
</style>