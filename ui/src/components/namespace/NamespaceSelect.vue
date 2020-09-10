<template>
    <b-form-group>
        <v-select
            v-model="selectedNamespace"

            @input="onNamespaceSelect"
            :placeholder="$t('Select namespace')"
            :options="namespaces"
            class="ns-selector"
        ></v-select>
    </b-form-group>
</template>
<script>
import { mapState } from "vuex";
export default {
    created() {
        this.$store.dispatch("namespace/loadNamespaces");
        this.selectedNamespace = this.$route.query.namespace || "";
    },
    computed: {
        ...mapState("namespace", ["namespaces"])
    },
    data() {
        return {
            selectedNamespace: ""
        };
    },
    methods: {
        onNamespaceSelect() {
            const query = { ...this.$route.query };
            query.namespace = this.selectedNamespace;
            if (!this.selectedNamespace) {
                delete query.namespace;
            }
            this.$router.push({ query });
            this.$emit("onNamespaceSelect");
        }
    }
};
</script>


<style lang="scss" scoped>
@import "../../styles/_variable.scss";
@include media-breakpoint-up(md) {
    .ns-selector {
        width:550px;
    }
}
</style>
