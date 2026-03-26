export function useTheme() {
    const isDark = () => {
        return document.getElementsByTagName("html")[0].className.indexOf("dark") >= 0
    };

    return {
        isDark,
        isWhite: () => !isDark(),
    };
}
