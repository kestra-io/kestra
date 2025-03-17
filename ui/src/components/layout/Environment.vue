<template>
    <div data-component="FILENAME_PLACEHOLDER" v-if="name" id="environment">
        <strong>{{ name }}</strong>
    </div>
</template>

<script>
    import {mapGetters} from "vuex";
    import {cssVariable} from "@kestra-io/ui-libs";

    export default {
        computed: {
            ...mapGetters("layout", ["envName", "envColor"]),
            ...mapGetters("misc", ["configs"]),
            name() {
                return this.envName || this.configs?.environment?.name;
            },
            color() {
                if (this.envColor) {
                    return this.envColor;
                }

                if (this.configs?.environment?.color) {
                    return this.configs.environment.color;
                }

                return cssVariable("--bs-info");
            }
        }
    }
</script>

<style lang="scss" scoped>
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