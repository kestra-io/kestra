<template>
    <div ref="root" class="execution-summary">
        <KsCard shadow="always" :bodyStyle="BODY_STYLE" class="card">
            <Banner :execution />
        </KsCard>
    </div>
</template>

<script setup lang="ts">
    import {onBeforeUnmount, useTemplateRef, watchEffect} from "vue"
    import {useElementSize} from "@vueuse/core"
    import type {Execution} from "../../../stores/executions"
    import Banner from "./Banner.vue"

    const BODY_STYLE = {padding: "0", height: "100%"}

    defineProps<{execution: Execution}>()

    const height = defineModel<number>("height", {default: 0})

    const root = useTemplateRef<HTMLElement>("root")
    const {height: measuredHeight} = useElementSize(root, undefined, {box: "border-box"})

    watchEffect(() => {
        height.value = measuredHeight.value
    })

    onBeforeUnmount(() => {
        height.value = 0
    })
</script>

<style scoped lang="scss">
    .execution-summary {
        padding-bottom: var(--ks-spacing-4);
    }

    .card {
        width: 100%;
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
    }
</style>
