<template>
    <div v-if="name" id="environment">
        <strong>{{ name }}</strong>
    </div>
</template>

<script setup lang="ts">
    import {cssVariable} from "@kestra-io/ui-libs";
    import {useLayoutStore} from "../../stores/layout";
    import {useMiscStore} from "override/stores/misc";
    import {computed} from "vue";

    const layoutStore = useLayoutStore();
    const miscStore = useMiscStore();

    const name = computed(() => {
        return layoutStore.envName || miscStore.configs?.environment?.name;
    })

    const color = computed(() => {
        if (layoutStore.envColor) {
            return layoutStore.envColor;
        }

        if (miscStore.configs?.environment?.color) {
            return miscStore.configs.environment.color;
        }

        return cssVariable("--kel-color-info");
    })

</script>

<style scoped lang="scss">
#environment {
    margin-bottom: 1.5rem;
    text-align: center;
    margin-top: -1.25rem;

    strong {
        border: 1px solid v-bind('color');
        border-radius: var(--kel-border-radius-base);
        color: var(--ks-content-primary);
        padding: 0.125rem 0.25rem;
        font-size: var(--kel-font-size-small);
        white-space: nowrap;
        text-overflow: ellipsis;
        overflow: hidden;
        max-width: 90%;
        display: inline-block;
    }
}
</style>