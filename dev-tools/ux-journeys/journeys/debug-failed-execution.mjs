export const journey = {
    id: "debug-failed-execution",
    title: "Diagnostiquer une exécution en échec",
    summary: "L'utilisateur filtre les exécutions en échec, ouvre celle de campaign_sync, lit l'erreur dans les logs puis tente un redémarrage. Comme l'erreur persiste, il ouvre le menu de la tâche fautive pour la rejouer à partir d'une révision choisie.",
    category: "Debug",
    area: "Executions",
    tags: ["failed", "logs", "restart", "replay", "erreur"],
    steps: [
        {
            key: "s1",
            file: "01.jpg",
            title: "Exécutions filtrées sur FAILED",
            caption: "La liste des exécutions est filtrée avec le chip « State in FAILED » sur les dernières 24 h : l'histogramme ne montre que des barres rouges et le tableau liste cinq exécutions de campaign_sync et process_orders. L'utilisateur clique sur l'id d'une exécution campaign_sync.",
            screen: "Exécutions › Liste",
            url: "/ui/main/executions?filters[state][IN]=FAILED",
            elements: ["Filter bar", "Chart", "Table", "Status badge", "Tag", "Button"],
            observation: "Le graphique regroupe toutes les exécutions dans une seule barre horaire quand elles sont rapprochées, ce qui le rend peu informatif."
        },
        {
            key: "s2",
            file: "02.jpg",
            title: "Gantt de l'exécution en échec",
            caption: "L'exécution s'ouvre sur le Gantt : fetch_metrics en Success, check_api en Failed. L'en-tête rappelle labels, révision, durée et attempts, avec « Fix with AI » et « Go to first failure ».",
            screen: "Exécution › Gantt",
            url: "/ui/main/executions/company.marketing/campaign_sync/<id>/gantt",
            elements: ["Gantt", "Status badge", "Tag", "Filter bar", "Tabs", "Breadcrumb", "Button"],
            observation: ""
        },
        {
            key: "s3",
            file: "03.jpg",
            title: "Logs avec l'erreur",
            caption: "Dans l'onglet Logs, les compteurs « (1) INFO » et « (2) ERROR » permettent de naviguer entre niveaux ; le bloc check_api est surligné en rouge avec le message « The social_ads API returned HTTP 503 Service Unavailable ».",
            screen: "Exécution › Logs",
            url: "/ui/main/executions/company.marketing/campaign_sync/<id>/logs",
            elements: ["Logs", "Filter bar", "Status badge", "Button"],
            observation: "Deux boutons « Restart » coexistent (en-tête de page et barre d'outils des logs) pour la même action."
        },
        {
            key: "s4",
            file: "04.jpg",
            title: "Confirmation du restart",
            caption: "Un clic sur « Restart » ouvre une confirmation « Are you sure you want to restart execution <id>? » avec Cancel et Restart.",
            screen: "Dialog › Restart",
            url: "/ui/main/executions/company.marketing/campaign_sync/<id>/logs",
            elements: ["Modal", "Button"],
            observation: "La confirmation n'indique pas ce qui sera rejoué (seulement les tâches en échec) ni l'impact sur l'exécution."
        },
        {
            key: "s5",
            file: "05.jpg",
            title: "Nouvelle tentative échouée",
            caption: "Après confirmation, l'en-tête affiche « Execution is Restarted », le label system.restarted: true et « 2 attempts » ; check_api passe en « Attempt 2/2 » et échoue avec la même erreur 503.",
            screen: "Exécution › Logs",
            url: "/ui/main/executions/company.marketing/campaign_sync/<id>/logs",
            elements: ["Logs", "Status badge", "Tag", "Dropdown"],
            observation: "Le sélecteur d'attempt par tâche permet de comparer les tentatives sans quitter la page."
        },
        {
            key: "s6",
            file: "06.jpg",
            title: "Menu d'actions de la tâche",
            caption: "Le menu ⋮ de check_api propose Fix with AI, Metrics, Outputs, Replay, Change state, Show task source, Download/Copy/Delete logs, Worker Information et Interrupt.",
            screen: "Exécution › Logs",
            url: "/ui/main/executions/company.marketing/campaign_sync/<id>/logs",
            elements: ["Dropdown", "Logs"],
            observation: "Le menu est long (11 entrées) et recouvre l'en-tête de l'exécution ; les actions destructrices (Delete logs) côtoient les actions de lecture."
        },
        {
            key: "s7",
            file: "07.jpg",
            title: "Dialogue Replay",
            caption: "« Replay execution » crée une nouvelle exécution à partir de l'exécution sélectionnée et demande la révision à utiliser : originale, dernière ou spécifique. Le bouton principal s'intitule « Replay from check_api ».",
            screen: "Dialog › Replay",
            url: "/ui/main/executions/company.marketing/campaign_sync/<id>/logs",
            elements: ["Modal", "Form", "Button"],
            observation: "Le libellé du bouton reprend le nom de la tâche, ce qui rend explicite le point de reprise."
        }
    ],
    edges: [
        {from: "s1", to: "s2", label: "Clic sur l'id de l'exécution"},
        {from: "s2", to: "s3", label: "Onglet « Logs »"},
        {from: "s3", to: "s4", label: "Clic sur « Restart »"},
        {from: "s4", to: "s3", label: "Clic sur « Cancel »"},
        {from: "s4", to: "s5", label: "Clic sur « Restart »"},
        {from: "s5", to: "s6", label: "Clic sur ⋮ de check_api"},
        {from: "s6", to: "s7", label: "Clic sur « Replay »"}
    ]
}

function restartDialog(page) {
    return page.locator(".kel-overlay-dialog").filter({visible: true})
}

function taskMenuButton(page, taskId) {
    return page.locator(".taskrun-header")
        .filter({has: page.getByText(taskId, {exact: true})})
        .first()
        .getByRole("button", {name: "Actions"})
}

export async function capture({page, shot}) {
    await page.goto("/ui/main/executions?filters[state][IN]=FAILED&filters[timeRange][EQUALS]=PT24H")
    await shot("s1", {wait: 1500})

    await page.locator("a[href*=\"/executions/company.marketing/campaign_sync/\"]").first().click()
    await page.waitForURL(/campaign_sync\/\w+\/gantt/)
    await shot("s2", {wait: 1500})

    await page.locator("a[href*=\"/executions/\"][href*=\"/logs\"]").first().click()
    await shot("s3", {wait: 1500})

    await page.getByRole("button", {name: "Restart"}).first().click()
    await page.waitForTimeout(1000)
    await shot("s4", {instant: true})

    await restartDialog(page).getByRole("button", {name: "Restart"}).click()
    await page.waitForTimeout(6000)
    await shot("s5", {wait: 1000})

    await taskMenuButton(page, "check_api").click()
    await page.waitForTimeout(800)
    await shot("s6", {instant: true})

    await page.getByRole("menuitem", {name: "Replay"}).filter({visible: true}).click()
    await page.waitForTimeout(1500)
    await shot("s7", {instant: true})
}
