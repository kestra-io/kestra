<template>
    <div v-if="revisions && revisions.length > 1">
        <b-row>
            <b-col md="12 mb-3">
                <b-form-select v-model="sideBySide" :options="displayTypes" />
            </b-col>

            <b-col md="6 mb-3">
                <b-input-group>
                    <b-form-select @input="addQuery" v-model="revisionLeft" :options="options" />
                    <b-btn @click="seeRevision(revisionLeft, revisionLeftText)">
                        <kicon placement="bottomright" :tooltip="$t('see full revision')">
                            <file-code />
                        </kicon>
                    </b-btn>
                </b-input-group>

                <crud class="mt-3" permission="FLOW" :detail="{namespace: $route.params.namespace, flowId: $route.params.id, revision: revisionNumber(revisionLeft)}" />
            </b-col>
            <b-col md="6 mb-3">
                <b-input-group>
                    <b-form-select @input="addQuery" v-model="revisionRight" :options="options" />
                    <b-btn @click="seeRevision(revisionRight, revisionRightText)">
                        <kicon placement="bottomright" :tooltip="$t('see full revision')">
                            <file-code />
                        </kicon>
                    </b-btn>
                </b-input-group>

                <crud class="mt-3" permission="FLOW" :detail="{namespace: $route.params.namespace, flowId: $route.params.id, revision: revisionNumber(revisionRight)}" />
            </b-col>
            <b-col md="12" ref="editorContainer" class="editor-wrap">
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
            header-bg-variant="dark"
            header-text-variant="light"
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
    import Kicon from "../Kicon"
    import Crud from "override/components/auth/Crud";

    export default {
        components: {Editor, FileCode, Kicon, Crud},
        created() {
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
        methods: {
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
            addQuery() {
                this.$router.push({query: {
                    ...this.$route.query,
                    ...{revisionLeft:this.revisionLeft, revisionRight: this.revisionRight}}
                });
            }
        },
        computed: {
            ...mapState("flow", ["revisions"]),
            options() {
                return (this.revisions || []).map((revision, x) => {
                    return {
                        value: x,
                        text: revision.revision,
                    };
                });
            },
            revisionLeftText() {
                if (this.revisionLeft === undefined) {
                    return "";
                }
                return YamlUtils.stringify(this.revisions[this.revisionLeft]);
            },
            revisionRightText() {
                if (this.revisionRight === undefined) {
                    return "";
                }
                return YamlUtils.stringify(this.revisions[this.revisionRight]);
            },
            diff() {
                const linesLeft = this.revisionLeftText.split("\n");
                const linesRight = this.revisionRightText.split("\n");
                const minLength = Math.min(linesLeft.length, linesRight.length);
                const diff = [];
                for (let i = 0; i < minLength; i++) {
                    diff.push(linesLeft[i] === linesRight[i] ? "" : "≠");
                }
                return diff.join("\n");
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
                    {value: true, text: "side-by-side"},
                    {value: false, text: "line-by-line"},
                ],
            };
        },
    };
</script>
