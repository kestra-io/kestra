/** Each call starts a new request and returns a check that stays true only until a newer request starts, so a slower, older response can be dropped. */
export function latestRequest(): () => () => boolean {
    let latest = 0
    return () => {
        const request = ++latest
        return () => request === latest
    }
}
