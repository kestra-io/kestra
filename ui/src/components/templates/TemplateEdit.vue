<template>
    <div>
        <template-editor @on-save="save" v-model="content" lang="yaml" />
        <bottom-line v-if="canSave || canDelete">
            <ul>
                <li>
                    <el-button type="danger" v-if="canDelete" @click="deleteFile">
                        <kicon>
                            <delete />
                            <span>{{ $t('delete') }}</span>
                        </kicon>
                    </el-button>

                    <template v-if="canSave">
                        <el-button @click="save" type="primary">
                            <kicon>
                                <content-save /> {{ $t('save') }}
                            </kicon>
                        </el-button>
                    </template>
                </li>
            </ul>
        </bottom-line>
    </div>
</template>

<script>
    import flowTemplateEdit from "../../mixins/flowTemplateEdit";
    import {mapState} from "vuex";
    import unsavedChange from "../../mixins/unsavedChange";
    import Kicon from "../Kicon"

    export default {
        components: {Kicon},
        mixins: [flowTemplateEdit],
        data() {
            return {
                dataType: "template",
            };
        },
        computed: {
            ...mapState("template", ["template"]),
        },
        watch: {
            "$route.params"() {
                this.reload()
            },
        },
        created() {
            unsavedChange.methods.created.call(this);
            this.reload()
        },
        beforeUnmount() {
            unsavedChange.methods.beforeUnmount.call(this);
        },
        unmounted() {
            this.$store.commit("template/setTemplate", undefined);
        },
        methods: {
            reload() {
                if (this.$route.name === "templates/update") {
                    this.$store
                        .dispatch("template/loadTemplate", this.$route.params)
                        .then(this.loadFile);
                }
            }
        }

    };
</script>
