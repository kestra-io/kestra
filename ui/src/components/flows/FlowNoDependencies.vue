<template>
    <div class="no-dependencies-container">
        <div>
            <img :src="flowImage" alt="No dependencies">
        </div>
        <div class="no-dependencies-message">
            <p>{{ $t("flow no dependencies") }}</p>
        </div>
        <div class="no-dependencies-doc-message">
            <p>
                {{ $t("Read more about") }}
                <a
                    :href="dependenciesDocsUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="no-dependencies-doc-message doc-link"
                >
                    {{ $t("flow-dependencies") }}
                </a>
                {{ $t("in our documentation.") }}
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
            dependenciesDocsUrl() {
                return "https://kestra.io/docs/ui/flows#dependencies";
            }
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
  color: var(--el-text-color-primary);
}
.doc-link {
  text-decoration: underline;
  font-weight: bold;
}
.doc-link:hover {
  cursor: pointer;
}
</style>