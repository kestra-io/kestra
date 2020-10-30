<template>
    <div>
        <editor @onSave="save" v-model="content" lang="yaml"></editor>
        <bottom-line v-if="canSave || canDelete">
            <ul class="navbar-nav ml-auto">
                <li class="nav-item">
                    <b-button class="btn-danger" v-if="canDelete" @click="deleteFile">
                        <delete />
                        <span>{{$t('delete')}}</span>
                    </b-button>

                    <b-button @click="save" v-if="canSave">
                        <content-save />
                        <span>{{$t('save')}}</span>
                    </b-button>
                </li>
            </ul>
        </bottom-line>
    </div>
</template>

<script>
import flowTemplateEdit from "../../mixins/flowTemplateEdit";
import { mapGetters } from "vuex";

export default {
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
