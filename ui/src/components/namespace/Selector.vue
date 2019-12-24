<template>
    <b-form-group :label="$t('Select namespace')" label-cols-sm="auto">
        <v-select
            v-model="selectedNamespace"
            @search="onNamespaceSearch"
            @input="onNamespaceSelect"
            :options="namespaces"
        ></v-select>
    </b-form-group>
</template>
<script>
import { mapState } from "vuex";

export default {
    created() {
        this.$store.dispatch("namespace/loadNamespaces", { prefix: "" });
    },
    computed: {
        ...mapState("namespace", ["namespaces", "namespace"]),
        selectedNamespace: {
            set(namespace) {
                this.$store.commit("namespace/setNamespace", namespace);
            },
            get() {
                return this.namespace;
            }
        }
    },
    methods: {
        onNamespaceSearch(prefix) {
            if (prefix.length >= 3) {
                this.$store.dispatch("namespace/loadNamespaces", { prefix });
            }
        },
        onNamespaceSelect() {
            this.$emit("onNamespaceSelect", this.selectedNamespace);
        }
    }
};
</script>