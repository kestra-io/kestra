<template>
    <div v-if="revisions && revisions.length > 1">
        <b-row>
            <b-col md="12" class="mb-3">
                <b-form-select v-model="sideBySide" :options="displayTypes" />
            </b-col>

            <b-col md="6">
                <b-input-group>
                    <b-form-select @input="addQuery" v-model="revisionLeft" :options="options" />
                    <b-input-group-append>
                        <b-btn @click="seeRevision(revisionLeft, revisionLeftText)">
                            <kicon placement="bottomright" :tooltip="$t('see full revision')">
                                <file-code />
                                <span class="d-none d-lg-inline-block">&nbsp;{{ $t('see full revision') }}</span>
                            </kicon>
                        </b-btn>
                        <b-btn :disabled="revisionNumber(revisionLeft) === flow.revision" @click="restoreRevision(revisionLeft, revisionLeftText)">
                            <kicon placement="bottomright" :tooltip="$t('see full revision')">
                                <restore />
                                <span class="d-none d-lg-inline-block">&nbsp;{{ $t('restore') }}</span>
                            </kicon>
                        </b-btn>
                    </b-input-group-append>
                </b-input-group>

                <b-alert v-if="revisionLeftError" variant="warning" class="mb-0 mt-3" show>
                    <strong>{{ $t('invalid source') }}</strong><br>
                    {{ revisionLeftError }}
                </b-alert>

                <crud class="mt-3" permission="FLOW" :detail="{namespace: $route.params.namespace, flowId: $route.params.id, revision: revisionNumber(revisionLeft)}" />
            </b-col>
            <b-col md="6">
                <b-input-group>
                    <b-form-select @input="addQuery" v-model="revisionRight" :options="options" />
                    <b-input-group-append>
                        <b-btn @click="seeRevision(revisionRight, revisionRightText)">
                            <kicon placement="bottomright" :tooltip="$t('see full revision')">
                                <file-code />
                                <span class="d-none d-lg-inline-block">&nbsp;{{ $t('see full revision') }}</span>
                            </kicon>
                        </b-btn>
                        <b-btn :disabled="revisionNumber(revisionRight) === flow.revision" @click="restoreRevision(revisionRight, revisionRightText)">
                            <kicon placement="bottomright" :tooltip="$t('see full revision')">
                                <restore />
                                <span class="d-none d-lg-inline-block">&nbsp;{{ $t('restore') }}</span>
                            </kicon>
                        </b-btn>
                    </b-input-group-append>
                </b-input-group>

                <b-alert v-if="revisionRightError" variant="warning" class="mb-0 mt-3" show>
                    <strong>{{ $t('invalid source') }}</strong><br>
                    {{ revisionRightError }}
                </b-alert>

                <crud class="mt-3" permission="FLOW" :detail="{namespace: $route.params.namespace, flowId: $route.params.id, revision: revisionNumber(revisionRight)}" />
            </b-col>
            <b-col md="12" ref="editorContainer" class="mt-3 editor-wrap">
                <Editor
                    :diff-side-by-side="sideBySide"
                    :value="revisionRightText"
                    :original="revisionLeftText"
                    lang="yaml"
                />
            </b-col>
        </b-row>

        <b-modal
            :id="`modal-source-${revisionId}`"
            :title="`Revision ${revision}`"
            hide-backdrop
            hide-footer
            modal-class="right"
            size="xl"
        >
            <editor v-model="revisionYaml" lang="yaml" />
        </b-modal>
    </div>
    <div v-else>
        <b-alert class="mb-0" show>
            {{ $t('no revisions found') }}
        </b-alert>
    </div>
</template>
<script>
    import {mapState} from "vuex";
    import YamlUtils from "../../utils/yamlUtils";
    import Editor from "../../components/inputs/Editor";
    import FileCode from "vue-material-design-icons/FileCode";
    import Restore from "vue-material-design-icons/Restore";
    import Kicon from "../Kicon"
    import Crud from "override/components/auth/Crud";
    import {saveFlowTemplate} from "../../utils/flowTemplate";

    export default {
        components: {Editor, FileCode, Restore, Kicon, Crud},
        created() {
            this.load();
        },
        methods: {
            load() {
                this.$store
                    .dispatch("flow/loadRevisions", this.$route.params)
                    .then(() => {
                        const revisionLength = this.revisions.length;
                        if (revisionLength > 0) {
                            this.revisionRight = revisionLength - 1;
                        }
                        if (revisionLength > 1) {
                            this.revisionLeft = revisionLength - 2;
                        }
                        if (this.$route.query.revisionRight) {
                            this.revisionRight = this.revisionIndex(
                                this.$route.query.revisionRight
                            );
                            if (
                                !this.$route.query.revisionLeft &&
                                this.revisionRight > 0
                            ) {
                                this.revisionLeft = this.revisions.length - 1;
                            }
                        }
                        if (this.$route.query.revisionLeft) {
                            this.revisionLeft = this.revisionIndex(
                                this.$route.query.revisionLeft
                            );
                        }
                    });
            },
            revisionIndex(revision) {
                const rev = parseInt(revision);
                for (let i = 0; i < this.revisions.length; i++) {
                    if (rev === this.revisions[i].revision) {
                        return i;
                    }
                }
            },
            revisionNumber(index) {
                return this.revisions[index].revision;
            },
            seeRevision(index, revision) {
                this.revisionId = index
                this.revisionYaml = revision
                this.revision = this.revisionNumber(index)
                setTimeout(() => {
                    this.$bvModal.show(`modal-source-${index}`)
                })
            },
            restoreRevision(index, revision) {
                this.$toast()
                    .confirm(this.$t("restore confirm", {revision: this.revisionNumber(index)}), () => {
                        return saveFlowTemplate(this, YamlUtils.parse(revision), "flow")
                            .then(this.load)
                            .then(() => {
                                this.$router.push({query: {}});
                            });
                    });
            },
            addQuery() {
                this.$router.push({query: {
                    ...this.$route.query,
                    ...{revisionLeft:this.revisionLeft + 1, revisionRight: this.revisionRight + 1}}
                });
            },
            transformRevision(source) {
                if (source.exception) {
                    return YamlUtils.stringify(YamlUtils.parse(source.source));
                }

                return YamlUtils.stringify(source);
            }
        },
        computed: {
            ...mapState("flow", ["flow", "revisions"]),
            options() {
                return (this.revisions || []).map((revision, x) => {
                    return {
                        value: x,
                        text: revision.revision,
                    };
                });
            },
            revisionLeftError() {
                if (this.revisionLeft === undefined) {
                    return "";
                }

                return this.revisions[this.revisionLeft].exception
            },
            revisionRightError() {
                if (this.revisionRight === undefined) {
                    return "";
                }

                return this.revisions[this.revisionRight].exception
            },
            revisionLeftText() {
                if (this.revisionLeft === undefined) {
                    return "";
                }

                return this.transformRevision(this.revisions[this.revisionLeft]);
            },
            revisionRightText() {
                if (this.revisionRight === undefined) {
                    return "";
                }

                return this.transformRevision(this.revisions[this.revisionRight]);
            },
        },
        data() {
            return {
                revisionLeft: 0,
                revisionRight: 0,
                revision: undefined,
                revisionId: undefined,
                revisionYaml: undefined,
                sideBySide: true,
                displayTypes: [
                    {value: true, text: this.$t("side-by-side")},
                    {value: false, text:  this.$t("line-by-line")},
                ],
            };
        },
    };
</script>
