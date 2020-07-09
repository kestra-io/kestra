<template>
    <b-form-group>
        <v-select
            v-model="selectedNamespace"
            @search="onNamespaceSearch"
            @input="onNamespaceSelect"
            :placeholder="$t('Select namespace')"
            :options="namespaces"
        ></v-select>
    </b-form-group>
</template>
<script>
import { mapState } from "vuex";
export default {
    created() {
        this.$store.dispatch("namespace/loadNamespaces", { prefix: "" });
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
        onNamespaceSearch(prefix) {
            if (prefix.length >= 3) {
                this.$store.dispatch("namespace/loadNamespaces", { prefix });
            }
        },
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
