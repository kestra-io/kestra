import {Ref, watch} from "vue"

export default function useRouteContext(routeInfo: Ref<{title: string}>, embed: boolean = false) {
    function handleTitle(){
        if(!embed) {
            let baseTitle

            if (document.title.lastIndexOf("|") >= 0) {
                baseTitle = document.title.substring(document.title.lastIndexOf("|") + 1).trim()
            } else {
                baseTitle = document.title
            }

            document.title = (routeInfo.value?.title ?? "") + " | " + baseTitle
        }
    }

    watch(() => routeInfo.value?.title, handleTitle, {immediate: true})
}
