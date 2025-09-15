import {ref, onMounted, watch, Ref} from "vue";
import {useRoute} from "vue-router";

export default (routeInfo: Ref<{title:string}>) => {

    const embed = ref(false);

    const route = useRoute();

    const handleTitle = () => {
        if(!embed.value) {
            let baseTitle;

            if (document.title.lastIndexOf("|") > 0) {
                baseTitle = document.title.substring(document.title.lastIndexOf("|") + 1);
            } else {
                baseTitle = document.title;
            }

            document.title = routeInfo.value?.title + " | " + baseTitle;
        }
    }

    onMounted(() => {
        handleTitle();
    })

    watch(route, () => {
        handleTitle();
    })

    return {
        embed
    }
}