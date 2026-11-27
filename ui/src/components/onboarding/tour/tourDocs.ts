const DOCS_UTM = "?utm_source=app&utm_medium=referral&utm_campaign=product-tour"

export const TOUR_DOCKER_DOCS = "https://kestra.io/docs/installation/docker#install-kestra-with-a-single-docker-container"

export const tourDocsUrl = (url: string) => {
    const [base, fragment] = url.split("#")
    return `${base}${DOCS_UTM}${fragment ? `#${fragment}` : ""}`
}
