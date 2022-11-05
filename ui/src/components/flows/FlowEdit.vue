<template>
    <div>
        <flow-editor @onSave="save" v-model="content" lang="yaml" />
        <bottom-line v-if="canSave || canDelete || canExecute">
            <ul class="navbar-nav ml-auto">
                <li class="nav-item">
                    <b-button class="btn-danger" v-if="canDelete" @click="deleteFile">
                        <kicon>
                            <delete />
                            <span>{{ $t('delete') }}</span>
                        </kicon>
                    </b-button>

                    <trigger-flow v-if="flow && canExecute" :disabled="flow.disabled" :flow-id="flow.id" :namespace="flow.namespace" />


                    <router-link v-if="flow" :to="{name: 'flows/create', query: {copy: true}}">
                        <b-button variant="primary">
                            <kicon>
                                <content-copy />
                                {{ $t('copy') }}
                            </kicon>
                        </b-button>
                    </router-link>

                    <b-button @click="save" v-if="canSave" variant="primary">
                        <kicon :tooltip="'(Ctrl + s)'">
                            <content-save />
                            <span>{{ $t('save') }}</span>
                        </kicon>
                    </b-button>
                </li>
            </ul>
        </bottom-line>
    </div>
</template>

<script>
    import flowTemplateEdit from "../../mixins/flowTemplateEdit";
    import unsavedChange from "../../mixins/unsavedChange";
    import {mapGetters} from "vuex";
    import TriggerFlow from "./TriggerFlow"
    import Kicon from "../Kicon"
    import ContentCopy from "vue-material-design-icons/ContentCopy";

    export default {
        components: {
            TriggerFlow,
            Kicon,
            ContentCopy,
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
            unsavedChange.methods.created.call(this);
            this.loadFile();
        },
        beforeUnmount() {
            unsavedChange.methods.beforeDestroy.call(this);
        },
    };
</script>
