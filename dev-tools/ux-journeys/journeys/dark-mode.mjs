export const journey = {
    id: "dark-mode",
    title: "Passer l'interface en thème sombre",
    summary: "L'utilisateur ouvre ses préférences, choisit le thème « Dark 2.0 » dans le sélecteur visuel de Color mode, puis parcourt le dashboard, l'éditeur de flow et une exécution en échec pour vérifier le rendu sombre.",
    category: "Configuration",
    area: "Settings",
    theme: "dark",
    tags: ["thème", "dark mode", "préférences", "apparence"],
    steps: [
        {
            key: "s1",
            file: "01.jpg",
            title: "Page des préférences",
            caption: "La page Settings affiche un menu à gauche (Main Configuration › Preferences, Instance verrouillé) et une longue liste de réglages : namespace par défaut, niveau de log, type d'éditeur, onglets par défaut, etc. L'interface est encore en clair.",
            screen: "Paramètres",
            url: "/ui/main/preferences",
            elements: ["Form", "Dropdown", "Sidebar"],
            observation: "Le fil d'Ariane dit « Settings », le menu « Preferences » et le titre « Main Configuration » : trois noms pour la même page."
        },
        {
            key: "s2",
            file: "02.jpg",
            title: "Section Appearance",
            caption: "Plus bas, la section Appearance propose « Color mode » sous forme de quatre miniatures (Dark 2.0, Dark 1.0, Light, Sync with system), avec l'astuce Ctrl+Shift+L, puis les tailles de police de l'app, des logs et de l'éditeur.",
            screen: "Paramètres",
            url: "/ui/main/preferences",
            elements: ["Form", "Card", "Dropdown"],
            observation: "Les miniatures montrent chaque thème appliqué à l'interface, plus parlant qu'une simple liste."
        },
        {
            key: "s3",
            file: "03.jpg",
            title: "Thème Dark 2.0 appliqué",
            caption: "Un clic sur « Dark 2.0 » applique immédiatement le thème sombre à toute l'interface, sans bouton d'enregistrement ; la miniature est mise en évidence.",
            screen: "Paramètres",
            url: "/ui/main/preferences",
            elements: ["Form", "Card", "Sidebar"],
            observation: ""
        },
        {
            key: "s4",
            file: "04.jpg",
            title: "Dashboard en sombre",
            caption: "Le Default Dashboard en thème sombre : KPI (Success Ratio 71,43 %, Failed Ratio 14,29 %, In Progress 2), histogramme, donut de 35 exécutions et tableau des exécutions avec badges d'état.",
            screen: "Dashboard",
            url: "/ui/main/dashboards",
            elements: ["Chart", "Card", "Table", "Status badge", "Filter bar"],
            observation: "Les couleurs d'état gardent un bon contraste sur fond sombre."
        },
        {
            key: "s5",
            file: "05.jpg",
            title: "Éditeur de flow en sombre",
            caption: "L'éditeur de process_orders en sombre : YAML coloré à gauche et topologie verticale à droite, avec les blocs Parallel et If sur fond bleu nuit.",
            screen: "Flow › Éditeur",
            url: "/ui/main/flows/edit/company.data/process_orders/edit",
            elements: ["Code editor", "Topology graph", "Split view", "Tabs"],
            observation: ""
        },
        {
            key: "s6",
            file: "06.jpg",
            title: "Exécution en échec en sombre",
            caption: "La vue d'ensemble d'une exécution process_orders en échec : badge Failed, alerte rouge « Execution failed! » et topologie avec les tâches en échec encadrées de rouge.",
            screen: "Exécution › Topologie",
            url: "/ui/main/executions/company.data/process_orders/<id>/overview",
            elements: ["Alert", "Topology graph", "Status badge", "Tag", "Button"],
            observation: "Comme en clair, les nœuds du graphe sont petits et leurs libellés peu lisibles, encore moins sur fond sombre."
        }
    ],
    edges: [
        {from: "s1", to: "s2", label: "Défilement jusqu'à Appearance"},
        {from: "s2", to: "s3", label: "Clic sur « Dark 2.0 »"},
        {from: "s3", to: "s4", label: "Sidebar › Dashboards"},
        {from: "s4", to: "s5", label: "Ouverture d'un flow"},
        {from: "s5", to: "s6", label: "Ouverture d'une exécution"}
    ]
}

export async function capture({page, shot, api}) {
    const failedRun = await api.latestExecution("company.data", "process_orders", "FAILED")

    await page.goto("/ui/main/preferences")
    await shot("s1", {wait: 1500})

    await page.getByText("Color mode", {exact: true}).first().evaluate((element) => element.scrollIntoView({block: "center"}))
    await shot("s2")

    await page.getByText("Dark 2.0", {exact: true}).first().click()
    await page.waitForTimeout(1200)
    await shot("s3")

    await page.goto("/ui/main/dashboards")
    await shot("s4", {wait: 2500})

    await page.goto("/ui/main/flows/edit/company.data/process_orders/edit")
    await page.waitForTimeout(1500)
    await page.getByText("Topology", {exact: true}).first().click()
    await shot("s5", {wait: 2000})

    await page.goto(`/ui/main/executions/company.data/process_orders/${failedRun.id}/overview`)
    await shot("s6", {wait: 2500})
}
