<template>
    <v-select
        :value="value"
        @input="onInput"
        :placeholder="$t('Select namespace')"
        :options="groupedNamespaces"
        :reduce="namespace => namespace.code"
        class="ns-selector"
    >
        <template #option="{label,level}">
            <span :class="'level-'+level">{{ label }}</span>
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
            },
            value: {
                type: String,
                default: undefined
            }
        },
        created() {
            this.$store
                .dispatch("namespace/loadNamespaces", {dataType: this.dataType})
                .then(() => {
                    this.groupedNamespaces = this.groupNamespaces(this.namespaces);
                });
        },
        computed: {
            ...mapState("namespace", ["namespaces"])
        },
        data() {
            return {
                groupedNamespaces: [],
            };
        },
        methods: {
            onInput(value) {
                this.$emit("input", value);
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
        //width:550px !important;
    }
}

.v-select{
    $levels: 10;
    $level-size: 6px;

    @mixin level-x {
        @for $i from 0 through $levels {
            .level-#{$i} {
                margin-left: $level-size * $i;
            }
        }
    }

    @include level-x;
}
</style>
