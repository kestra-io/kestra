import {visibleDropdownItems} from "../lib.mjs"

export const journey = {
    id: "logs-search",
    title: "Rechercher dans les logs globaux",
    summary: "L'utilisateur part de la page Logs globale, restreint le niveau à ERROR puis recherche le texte « 503 » pour retrouver une erreur d'API. Chaque valeur de log (namespace, flow, exécution) est actionnable et permet de rebondir vers l'exécution concernée.",
    category: "Recherche & filtres",
    area: "Logs",
    tags: ["logs", "niveau", "recherche texte", "navigation"],
    steps: [
        {
            key: "s1",
            file: "01.jpg",
            title: "Page Logs globale",
            caption: "La page Logs affiche les filtres « Log Level at or above INFO » et « Interval Last 24 hours », un histogramme par niveau (Info 68, Error 12, Warn 4), des compteurs par niveau et la liste des lignes avec namespace, flowId, taskId et executionId.",
            screen: "Logs",
            url: "/ui/main/logs",
            elements: ["Logs", "Filter bar", "Chart", "Search", "Tag"],
            observation: "Le compteur « (12) DEBUG » est affiché alors que le filtre exclut ce niveau et que le graphique ne le montre pas."
        },
        {
            key: "s2",
            file: "02.jpg",
            title: "Panneau Log Level",
            caption: "Un clic sur le chip Log Level ouvre un panneau avec un opérateur « At or Above » et une liste déroulante de niveau (INFO).",
            screen: "Logs",
            url: "/ui/main/logs",
            elements: ["Filter bar", "Dropdown", "Logs"],
            observation: ""
        },
        {
            key: "s3",
            file: "03.jpg",
            title: "Choix du niveau",
            caption: "La liste propose TRACE, DEBUG, INFO, WARN et ERROR, chacun avec sa pastille de couleur ; INFO est coché.",
            screen: "Logs",
            url: "/ui/main/logs",
            elements: ["Filter bar", "Dropdown"],
            observation: ""
        },
        {
            key: "s4",
            file: "04.jpg",
            title: "Logs ERROR uniquement",
            caption: "Avec « at or above ERROR », le graphique ne montre plus que 12 erreurs et la liste ne contient que des lignes ERROR (campaign_sync, newsletter_dispatch).",
            screen: "Logs",
            url: "/ui/main/logs?filters[level][GREATER_THAN_OR_EQUAL_TO]=ERROR",
            elements: ["Logs", "Chart", "Filter bar"],
            observation: ""
        },
        {
            key: "s5",
            file: "05.jpg",
            title: "Recherche « 503 »",
            caption: "La saisie de 503 dans « Search logs » réduit la liste à 3 lignes, avec l'occurrence surlignée dans le message « API returned HTTP 503 Service Unavailable ».",
            screen: "Logs",
            url: "/ui/main/logs?filters[q][EQUALS]=503",
            elements: ["Search", "Logs", "Chart", "Filter bar"],
            observation: "Le graphique n'applique pas la recherche texte : il affiche toujours Error (12) alors que la liste n'en montre que 3."
        },
        {
            key: "s6",
            file: "06.jpg",
            title: "Menu sur une valeur",
            caption: "Un clic sur l'executionId d'une ligne ouvre un petit menu « Copy value » / « Open page ».",
            screen: "Logs",
            url: "/ui/main/logs?filters[q][EQUALS]=503",
            elements: ["Dropdown", "Logs"],
            observation: "Chaque métadonnée de log est un point de navigation, sans surcharger la ligne de boutons."
        },
        {
            key: "s7",
            file: "07.jpg",
            title: "Exécution ouverte",
            caption: "« Open page » mène au Gantt de l'exécution campaign_sync concernée, dans le même onglet ; le filtre de niveau revient à INFO.",
            screen: "Exécution › Gantt",
            url: "/ui/main/executions/company.marketing/campaign_sync/<id>/gantt",
            elements: ["Gantt", "Status badge", "Filter bar", "Breadcrumb"],
            observation: "La recherche « 503 » et le niveau ERROR ne sont pas transmis à l'exécution ouverte."
        }
    ],
    edges: [
        {from: "s1", to: "s2", label: "Clic sur le chip « Log Level »"},
        {from: "s2", to: "s3", label: "Ouverture de la liste"},
        {from: "s3", to: "s4", label: "Choix de « ERROR »"},
        {from: "s4", to: "s5", label: "Saisie de « 503 »"},
        {from: "s5", to: "s6", label: "Clic sur l'executionId"},
        {from: "s6", to: "s7", label: "Clic sur « Open page »"}
    ]
}

function firstExecutionIdValue(page) {
    return page.locator(".log-meta").filter({hasText: "executionId"}).first().getByRole("button")
}

export async function capture({page, shot}) {
    await page.goto("/ui/main/logs")
    await shot("s1", {wait: 1500})

    await page.locator(".filter-chip").filter({hasText: "Log Level"}).first().click()
    await page.waitForTimeout(900)
    const levelPanel = page.locator(".edit-popper").first()
    await shot("s2", {instant: true})

    await levelPanel.locator(".kel-select").first().click()
    await page.waitForTimeout(700)
    await shot("s3", {instant: true})

    await visibleDropdownItems(page).filter({hasText: /^\s*ERROR\s*$/}).first().click()
    await page.waitForTimeout(600)
    await levelPanel.locator("button[aria-label=Close]").first().click()
    await shot("s4", {wait: 1500})

    await page.getByPlaceholder("Search logs").fill("503")
    await page.keyboard.press("Enter")
    await shot("s5", {wait: 1800})

    await firstExecutionIdValue(page).click()
    await page.waitForTimeout(2000)
    await shot("s6", {wait: 1500})

    await page.getByText("Open page", {exact: true}).filter({visible: true}).first().click()
    await page.waitForTimeout(1500)
    await shot("s7", {wait: 1500})
}
