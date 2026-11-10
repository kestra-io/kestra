import {Ref, watch} from "vue"
import {setDocumentTitle} from "../utils/documentTitle"

export default function useRouteContext(routeInfo: Ref<{title?: string | null}>, embed: boolean = false) {
    function handleTitle() {
        if (!embed) {
            setDocumentTitle(routeInfo.value?.title)
        }
    }

    watch(() => routeInfo.value?.title, handleTitle, {immediate: true})
}
