<template>
    <div v-if="name" id="environment">
        <strong>{{ name }}</strong>
    </div>
</template>

<script>
    import {mapGetters} from "vuex";
    import {cssVariable} from "../../utils/global";

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
    margin-bottom: 0.5em;
    background-color: v-bind('color');
    text-align: center;

    strong {
        color: var(--bs-body-bg);
    }
}
</style>