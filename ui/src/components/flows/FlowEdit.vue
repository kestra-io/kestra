<template>
    <div>
        <editor @onSave="save" v-model="content" lang="yaml" />
        <bottom-line v-if="canSave || canDelete">
            <ul class="navbar-nav ml-auto">
                <li class="nav-item">
                    <b-button class="btn-danger" v-if="canDelete" @click="deleteFile">
                        <kicon>
                            <delete />
                            <span>{{ $t('delete') }}</span>
                        </kicon>
                    </b-button>

                    <trigger-flow v-if="flow && canExecute" :flow-id="flow.id" :namespace="flow.namespace" />

                    <b-button @click="save" v-if="canSave">
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
    import {mapGetters} from "vuex";
    import TriggerFlow from "./TriggerFlow"
    import Kicon from "../Kicon"

    export default {
        components: {
            TriggerFlow,
            Kicon,
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
