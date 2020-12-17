<template>
    <div>
        <editor @onSave="save" v-model="content" lang="yaml" />
        <bottom-line v-if="canSave || canDelete">
            <ul class="navbar-nav ml-auto">
                <li class="nav-item">
                    <b-button class="btn-danger" v-if="canDelete" @click="deleteFile">
                        <delete />
                        <span>{{ $t('delete') }}</span>
                    </b-button>

                    <trigger-flow v-if="flow && canExecute" :flow-id="flow.id" :namespace="flow.namespace" />

                    <b-button @click="save" v-if="canSave" v-b-tooltip.hover.top="'(Ctrl + s)'">
                        <content-save />
                        <span>{{ $t('save') }}</span>
                    </b-button>
                </li>
            </ul>
        </bottom-line>
    </div>
</template>

<script>
    import flowTemplateEdit from "../../mixins/flowTemplateEdit";
    import {mapGetters} from "vuex";
    import TriggerFlow from "./TriggerFlow"

    export default {
        components: {
            TriggerFlow
        },
        mixins: [flowTemplateEdit],
        data() {
            return {
                dataType: "flow",
            };
        },
        computed: {
            ...mapGetters("flow", ["flow"]),
        },
        created() {
            this.loadFile();
        },
    };
</script>
