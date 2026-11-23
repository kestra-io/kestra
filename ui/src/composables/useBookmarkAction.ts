import {ref, shallowRef} from "vue"

interface BookmarkAction {
    bookmarked: boolean
    toggle: () => void
}

const bookmarkAction = shallowRef<BookmarkAction | undefined>()
const actionsMenuCount = ref(0)

export function useBookmarkAction() {
    return {bookmarkAction, actionsMenuCount}
}
