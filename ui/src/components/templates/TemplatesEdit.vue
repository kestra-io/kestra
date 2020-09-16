<template>
    <div>
        <editor v-model="content" lang="yaml"></editor>
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
import fileEdit from "../../mixins/fileEdit";
import { mapState } from "vuex";

export default {
    mixins: [fileEdit],
    data() {
        return {
            dataType: "template",
        };
    },
    computed: {
        ...mapState("template", ["template"]),
    },
};
</script>
