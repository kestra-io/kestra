<template>
    <div v-if="envName" id="environment">
        <strong>{{ envName }}</strong>
    </div>
</template>

<script>
    import {mapGetters} from "vuex";

    export default {
        computed: {
            ...mapGetters("layout", ["envName", "envColor"])
        }
    }
</script>

<style lang="scss" scoped>
#environment {
    margin-bottom: 0.5em;
    background-color: v-bind('envColor');
    text-align: center;

    strong {
        color: var(--bs-body-bg);
    }
}
</style>