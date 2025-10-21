<template>
    <div v-if="name" id="environment">
        <strong>{{ name }}</strong>
    </div>
</template>

<script>
    import {mapStores} from "pinia";
    import {cssVariable} from "@kestra-io/ui-libs";
    import {useLayoutStore} from "../../stores/layout";
    import {useMiscStore} from "override/stores/misc";

    export default {
        computed: {
            ...mapStores(useLayoutStore, useMiscStore),
            name() {
                return this.layoutStore.envName || this.miscStore.configs?.environment?.name;
            },
            color() {
                if (this.layoutStore.envColor) {
                    return this.layoutStore.envColor;
                }

                if (this.miscStore.configs?.environment?.color) {
                    return this.miscStore.configs.environment.color;
                }

                return cssVariable("--bs-info");
            }
        }
    }
</script>

<style scoped lang="scss">
#environment {
    margin-bottom: 1.5rem;
    text-align: center;
    margin-top: -1.25rem;

    strong {
        border: 1px solid v-bind('color');
        border-radius: var(--bs-border-radius);
        color: var(--ks-content-primary);
        padding: 0.125rem 0.25rem;
        font-size: var(--font-size-sm);
        white-space: nowrap;
        text-overflow: ellipsis;
        overflow: hidden;
        max-width: 90%;
        display: inline-block;
    }
}
</style>