<template>
    <div class="no-dependencies-container">
        <div>
            <img :src="flowImage" alt="No dependencies">
        </div>
        <div class="no-dependencies-message">
            <p>{{ $t("flow-no-dependencies") }}</p>
        </div>
        <div class="no-dependencies-doc-message" :class="themeClass">
            <p>
                {{ $t("read-more") }}
                <a
                    :href="dependenciesDocsUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="no-dependencies-doc-message doc-link"
                >
                    {{ $t("flow-dependencies") }}
                </a>
                {{ $t("in-our-documentation") }}
            </p>
        </div>
    </div>
</template>

<script>
    import flowImageDark from "../../assets/onboarding/onboarding-flow-no-dependency-dark.svg"
    import flowImageLight from "../../assets/onboarding/onboarding-flow-no-dependency-light.svg"
    export default {
        name: "NoDependencies",
        computed: {
            flowImage() {
                return (localStorage.getItem("theme") || "light") === "light" ? flowImageLight : flowImageDark;
            },
            themeClass() {
                return (localStorage.getItem("theme") || "light") === "light" ? "theme-light" : "theme-dark";
            },
            dependenciesDocsUrl() {
                return "https://kestra.io/docs/ui/flows#dependencies";
            },

        }
    };
</script>

<style scoped>
.no-dependencies-container {
  padding: 180px;
  text-align: center;
}
.no-dependencies-message {
  font-weight: bold;
  font-size: var(--el-font-size-small);
  color: var(--el-text-color-regular);
  margin-bottom: -10px;
}
.no-dependencies-doc-message {
  font-weight: 200;
  font-size: var(--el-font-size-extra-small);
}
.theme-light {
  color: #000; 
}
.theme-dark {
  color: #fff;
}
.doc-link {
  text-decoration: underline;
  font-weight: bold;
  color: var(--el-text-color-primary);
}
.doc-link:hover {
  cursor: pointer;
}
</style>