<template>
    <v-select
        v-model="selectedNamespace"
        @input="onNamespaceSelect"
        :placeholder="$t('Select namespace')"
        :options="groupedNamespaces"
        :reduce="namespace => namespace.code"
        class="ns-selector"
    >
        <template #option="{label,level}">
            <strong :class="'level-'+level">{{ label }}</strong>
        </template>
    </v-select>
</template>
<script>
    import {mapState} from "vuex";
    import _uniqBy from "lodash/uniqBy";

    export default {
        props: {
            dataType: {
                type: String,
                required: true
            }
        },
        created() {
            this.$store
                .dispatch("namespace/loadNamespaces", {dataType: this.dataType})
                .then(() => {
                    this.groupedNamespaces = this.groupNamespaces(this.namespaces);
                });
            this.selectedNamespace = this.$route.query.namespace || "";
        },
        computed: {
            ...mapState("namespace", ["namespaces"])
        },
        data() {
            return {
                selectedNamespace: "",
                groupedNamespaces: [],
            };
        },
        methods: {
            onNamespaceSelect() {
                const query = {...this.$route.query};
                query.namespace = this.selectedNamespace;
                if (!this.selectedNamespace) {
                    delete query.namespace;
                }
                this.$router.push({query});
                this.$emit("onNamespaceSelect");
            },
            groupNamespaces(namespaces){
                let res = [];
                namespaces.forEach(ns => {
                    // Let's say one of our namespace is com.domain.service.product
                    // We want to get the following "groups" from it :
                    // com
                    // com.domain
                    // com.domain.service
                    // com.domain.service.product

                    let parts = ns.split(".");
                    let previousPart = "";

                    parts.forEach(part => {
                        let currentPart = (previousPart ? previousPart + "." : "" ) + part;
                        let level = currentPart.split(".").length - 1;
                        res.push({code: currentPart, label: currentPart, level: level});
                        previousPart = currentPart;
                    });
                });

                // Remove duplicate namespaces ...
                return _uniqBy(res,"code");
            },
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

.v-select{
    $levels: 10;
    $level-size: 6px;
    $base-font-weight:800;

    @mixin level-x {
        @for $i from 0 through $levels {
            .level-#{$i} {
                font-weight: ($base-font-weight - ($i * 100));
                margin-left: $level-size * $i;
                color: lighten($dark, $i*5%);
            }
        }
    }

    @include level-x;
}
</style>
