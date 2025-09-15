import {Ref, watch} from "vue";
import {useRoute} from "vue-router";

export default function useRouteContext(routeInfoTitle: Ref<string>, embed: boolean = false) {
    const route = useRoute();

    function handleTitle(){
        if(!embed) {
            let baseTitle;

            if (document.title.lastIndexOf("|") > 0) {
                baseTitle = document.title.substring(document.title.lastIndexOf("|") + 1);
            } else {
                baseTitle = document.title;
            }

            document.title = routeInfoTitle.value + " | " + baseTitle;
        }
    }

    watch(() => route, () => {
        handleTitle()
    }, {immediate: true})
}
