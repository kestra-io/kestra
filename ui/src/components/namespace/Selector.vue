<template>
    <b-form-group :label="$t('Select namespace')">
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
    data () {
        return {
            selectedNamespace: ''
        }
    },
    created() {
        this.$store.dispatch('namespace/loadNamespaces', {prefix: ''})
    },
    computed: {
        ...mapState("namespace", ["namespaces"]),
    },
    methods: {
        onNamespaceSearch(prefix) {
            if (prefix.length >= 3) {
                this.$store.dispatch("namespace/loadNamespaces", { prefix });
            }
        },
        onNamespaceSelect() {
            this.$emit('onNamespaceSelect', this.selectedNamespace)
        }
    }
};
</script>