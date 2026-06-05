<template>
    <div v-if="name" id="environment">
        <span class="dot" />
        <span class="name" :title="name">{{ name }}</span>
        <span class="label">{{ t("environment") }}</span>
    </div>
</template>

<script setup lang="ts">
    import {cssVar} from "@kestra-io/design-system"
    import {useLayoutStore} from "../../stores/layout"
    import {useMiscStore} from "override/stores/misc"
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"

    const {t} = useI18n()
    const layoutStore = useLayoutStore()
    const miscStore = useMiscStore()

    const name = computed(() => {
        return layoutStore.envName || miscStore.configs?.environment?.name
    })

    const color = computed(() => {
        if (layoutStore.envColor) {
            return layoutStore.envColor
        }

        if (miscStore.configs?.environment?.color) {
            return miscStore.configs.environment.color
        }

        return cssVar("--ks-status-info")
    })

</script>

<style scoped lang="scss">
#environment {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    border: 1px solid v-bind('color');
    border-radius: var(--ks-radius-sm);
    padding: 0.125rem var(--ks-spacing-2);

    .dot {
        flex-shrink: 0;
        width: 0.25rem;
        height: 0.25rem;
        border-radius: 50%;
        background-color: v-bind('color');
    }

    .name {
        flex: 1;
        min-width: 0;
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-xs);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .label {
        flex-shrink: 0;
        color: var(--ks-text-inactive);
        font-size: var(--ks-font-size-2xs);
        white-space: nowrap;
    }
}
</style>
