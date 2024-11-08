<template>
    <el-empty :image="noDataImage" :description="$t('no_data')" />
</template>

<script>
    import noDataImageDark from "../../assets/dark.png";
    import noDataImageLight from "../../assets/light.png";
    
    export default {
        name: "NoDataAvailable",
        computed: {
            noDataImage() {
                return (localStorage.getItem("theme") || "light") === "light"
                    ? noDataImageLight
                    : noDataImageDark;
            },
        },
    };
</script>
