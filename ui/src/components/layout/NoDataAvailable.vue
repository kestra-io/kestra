<template>
  <el-empty :image="noDataImage" :description="$t('no_data')"/>
</template>

<script>
  import noDataImageDark from "../../assets/dark";
  import noDataImageLight from "../../assets/light";

  export default {
    name: "NoDataAvailable",
    },
    computed: {
      noDataImage() {
        return (localStorage.getItem("theme") || "light") === "light"
          ? "noDataImageLight"
          : "noDataImageDark";
      },
    },
  };
</script>