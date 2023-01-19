<script>
    import Editor from "../../../components/inputs/Editor.vue";
    import {apiRoot} from "../../../utils/axios";

    export default {
        extends: Editor,
        props: {
            schemas: {type: Array, default: () => [`${apiRoot}plugins/schemas/flow`]},
        },
    }
</script>