<template>
    <b-dropdown v-if="actionFlow" right id="dropdown-1" :text="$t('actions')" class="m-md-2">
        <b-dropdown-item>
            <router-link :to="`/flows/edit/${actionFlow.namespace}/${actionFlow.id}`">
                <edit />
                {{ $t('edit flow') }} {{ actionFlow.id }}
            </router-link>
        </b-dropdown-item>
        <b-dropdown-item>
            <router-link :to="`/executions/${actionFlow.namespace}/${actionFlow.id}`">
                <search />
                {{ $tc('display flow {id} executions', null, flow) }}
            </router-link>
        </b-dropdown-item>
        <b-dropdown-item>
            <router-link :to="{name: 'flowTopology', params: actionFlow}">
                <graph />
                {{ $t('display topology for flow') }} {{ actionFlow.id }}
            </router-link>
        </b-dropdown-item>
    </b-dropdown>
</template>
<script>
    import Search from "vue-material-design-icons/Magnify";
    import Edit from "vue-material-design-icons/Pencil";
    import Graph from "vue-material-design-icons/Graph";
    import {mapState} from "vuex";
    export default {
        components: {
            Search,
            Edit,
            Graph
        },
        props: {
            flowItem: {
                type: Object,
                default: undefined,
            }
        },
        computed: {
            ...mapState("flow", ["flow"]),
            actionFlow() {
                return this.flow || this.flowItem;
            }
        }
    };
</script>
