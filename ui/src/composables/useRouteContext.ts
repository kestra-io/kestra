import {Ref, watch} from "vue";
import {useRoute} from "vue-router";

export default function useRouteContext(routeInfo: Ref<{title: string}>, embed: boolean = false) {
    const route = useRoute();

    function handleTitle(){
        if(!embed) {
            let baseTitle;

            if (document.title.lastIndexOf("|") > 0) {
                baseTitle = document.title.substring(document.title.lastIndexOf("|") + 1);
            } else {
                baseTitle = document.title;
            }

            document.title = routeInfo.value?.title + " | " + baseTitle;
        }
    }

    watch(() => route, () => {
        handleTitle()
    }, {immediate: true})
}
