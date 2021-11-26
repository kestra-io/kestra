<template>
    <b-card>
        <template-editor @onSave="save" v-model="content" lang="yaml" />
        <bottom-line v-if="canSave || canDelete">
            <ul class="navbar-nav ml-auto">
                <li class="nav-item">
                    <b-button class="btn-danger" v-if="canDelete" @click="deleteFile">
                        <delete />
                        <span>{{ $t('delete') }}</span>
                    </b-button>

                    <b-button @click="save" v-if="canSave" variant="primary">
                        <content-save />
                        <span>{{ $t('save') }}</span>
                    </b-button>
                </li>
            </ul>
        </bottom-line>
    </b-card>
</template>

<script>
    import flowTemplateEdit from "../../mixins/flowTemplateEdit";
    import {mapState} from "vuex";
    import unsavedChange from "../../mixins/unsavedChange";

    export default {
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
        beforeDestroy() {
            unsavedChange.methods.beforeDestroy.call(this);
        },
        destroyed() {
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
