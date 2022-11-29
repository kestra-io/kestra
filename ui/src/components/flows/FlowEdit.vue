<template>
    <div>
        <flow-editor @save="save" v-model="content" lang="yaml" />
        <bottom-line v-if="canSave || canDelete || canExecute">
            <ul>
                <li>
                    <kicon>
                        <el-button type="danger" v-if="canDelete" @click="deleteFile">
                            <delete />
                            <span>{{ $t('delete') }}</span>
                        </el-button>
                    </kicon>
                </li>

                <li>
                    <router-link v-if="flow" :to="{name: 'flows/create', query: {copy: true}}">
                        <el-button>
                            <kicon>
                                <content-copy />
                                {{ $t('copy') }}
                            </kicon>
                        </el-button>
                    </router-link>
                </li>

                <li>
                    <trigger-flow v-if="flow && canExecute" :disabled="flow.disabled" :flow-id="flow.id" :namespace="flow.namespace" />
                </li>


                <li>
                    <el-button @click="save" v-if="canSave" type="primary">
                        <kicon>
                            <content-save />
                            <span>{{ $t('save') }}</span>
                        </kicon>
                    </el-button>
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
            unsavedChange.methods.beforeUnmount.call(this);
        },
    };
</script>
