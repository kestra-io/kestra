<script>
    import useDataComponent from "../composables/useDataComponent"

    export default {
        data() {
            const file = useDataComponent();

            return {
                dataComponent: file
            }
        },
    };
</script>