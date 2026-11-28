import {visibleDropdownItems} from "../lib.mjs"

export const journey = {
    id: "executions-filters",
    title: "Filtrer les exécutions par état et période",
    summary: "Sur une liste d'une trentaine d'exécutions, l'utilisateur isole les exécutions problématiques (échecs et warnings) puis resserre la période sur la dernière heure. La barre de filtres à chips met à jour l'URL, le graphique et le tableau, et propose une vue Timeline.",
    category: "Recherche & filtres",
    area: "Executions",
    tags: ["filtres", "état", "période", "chips", "timeline", "url"],
    steps: [
        {
            key: "s1",
            file: "01.jpg",
            title: "Liste complète des exécutions",
            caption: "La page Executions affiche par défaut les 30 derniers jours : histogramme empilé (Success 24, Failed 5, Warning 3, Paused 2) et tableau paginé de 34 exécutions. Les chips « State in any » et « Started Last 30 days » sont déjà présents.",
            screen: "Exécutions › Liste",
            url: "/ui/main/executions",
            elements: ["Filter bar", "Chart", "Table", "Status badge", "Pagination", "Search", "Button"],
            observation: "Un chip « State in any » est affiché alors qu'aucun filtre d'état n'est actif, ce qui alourdit la barre."
        },
        {
            key: "s2",
            file: "02.jpg",
            title: "Ajout d'un filtre",
            caption: "« Add filters » ouvre un panneau listant les clés disponibles (Namespace, Flow ID, Kind, State, Scope, Hierarchy…) avec description et un bouton +, une recherche et « Add advanced filter ». Le pied indique « 2 of 13 filters added ».",
            screen: "Exécutions › Liste",
            url: "/ui/main/executions",
            elements: ["Filter bar", "Dropdown", "Search", "Tooltip"],
            observation: ""
        },
        {
            key: "s3",
            file: "03.jpg",
            title: "États groupés",
            caption: "Le filtre State regroupe les états en familles (Running, Paused, Completed, Failed) avec compteurs. Cocher le groupe Failed sélectionne ses 4 états ; le groupe Completed est déplié pour montrer Success, Warning…",
            screen: "Exécutions › Liste",
            url: "/ui/main/executions?filters[state][IN]=FAILED,KILLING,KILLED,CANCELLED",
            elements: ["Filter bar", "Dropdown", "Search", "Chart", "Table"],
            observation: "Le regroupement par famille d'états est un raccourci efficace ; le tableau se met à jour en direct derrière le panneau."
        },
        {
            key: "s4",
            file: "04.jpg",
            title: "Ajout de Warning",
            caption: "Warning est coché dans le groupe Completed (compteur 1/5) : « 5 State selected », le chip affiche « State in FAILED +4 » et le tableau montre maintenant les newsletter_dispatch en Warning.",
            screen: "Exécutions › Liste",
            url: "/ui/main/executions?filters[state][IN]=FAILED,KILLING,KILLED,CANCELLED,WARNING",
            elements: ["Filter bar", "Dropdown", "Table", "Status badge"],
            observation: "Le chip « FAILED +4 » masque que Warning fait partie de la sélection : il faut rouvrir le panneau pour le savoir."
        },
        {
            key: "s5",
            file: "05.jpg",
            title: "Résultat filtré",
            caption: "Panneau fermé : l'histogramme ne contient plus que des barres rouges et orange, et le tableau liste les exécutions Failed et Warning.",
            screen: "Exécutions › Liste",
            url: "/ui/main/executions?filters[state][IN]=FAILED,KILLING,KILLED,CANCELLED,WARNING",
            elements: ["Filter bar", "Chart", "Table", "Status badge"],
            observation: ""
        },
        {
            key: "s6",
            file: "06.jpg",
            title: "Panneau d'intervalle",
            caption: "Un clic sur le chip « Started Last 30 days » ouvre le panneau Interval : onglets Predefined / Custom Range, une liste de périodes et un choix « Apply to » (Start date, End date, Start or end date).",
            screen: "Exécutions › Liste",
            url: "/ui/main/executions",
            elements: ["Filter bar", "Date picker", "Dropdown"],
            observation: "Le choix de la date de référence (début, fin, l'une ou l'autre) est rare et utile pour les exécutions longues."
        },
        {
            key: "s7",
            file: "07.jpg",
            title: "Choix d'une période",
            caption: "La liste déroulante propose des périodes de « Last 5 minutes » à « All time » ; « Last 30 days » est coché. L'utilisateur choisit « Last 1 hour ».",
            screen: "Exécutions › Liste",
            url: "/ui/main/executions",
            elements: ["Filter bar", "Dropdown", "Date picker"],
            observation: ""
        },
        {
            key: "s8",
            file: "08.jpg",
            title: "Résultat sur la dernière heure",
            caption: "Le chip devient « Started Last 1 hour », l'URL contient timeRange=PT1H et dateFilter=START_DATE, et l'histogramme se répartit maintenant en barres de quelques minutes avec une courbe de durée.",
            screen: "Exécutions › Liste",
            url: "/ui/main/executions?filters[timeRange][EQUALS]=PT1H&dateFilter=START_DATE",
            elements: ["Filter bar", "Chart", "Table", "Status badge"],
            observation: "Le graphique adapte sa granularité à la période : sur 30 jours tout tenait dans une barre, sur 1 h les exécutions se distinguent."
        },
        {
            key: "s9",
            file: "09.jpg",
            title: "Vue Timeline",
            caption: "Le bouton « Timeline » remplace l'histogramme par une frise : une mini-carte de la période, puis une ligne par namespace (company.marketing, company.data) avec les runs en échec positionnés dans le temps.",
            screen: "Exécutions › Liste",
            url: "/ui/main/executions?filters[timeRange][EQUALS]=PT1H",
            elements: ["Chart", "Filter bar", "Table", "Button"],
            observation: "La Timeline regroupe par namespace et affiche « 8 runs / 8 failed » : pratique pour repérer une vague d'échecs simultanés."
        }
    ],
    edges: [
        {from: "s1", to: "s2", label: "Clic sur « Add filters »"},
        {from: "s2", to: "s3", label: "Choix de « State » puis groupe Failed"},
        {from: "s3", to: "s4", label: "Coche de « Warning »"},
        {from: "s4", to: "s5", label: "Fermeture du panneau"},
        {from: "s5", to: "s6", label: "Clic sur le chip « Started »"},
        {from: "s6", to: "s7", label: "Ouverture de la liste des périodes"},
        {from: "s7", to: "s8", label: "Choix de « Last 1 hour »"},
        {from: "s8", to: "s9", label: "Clic sur « Timeline »"}
    ]
}

export async function capture({page, shot}) {
    await page.goto("/ui/main/executions")
    await shot("s1", {wait: 1500})

    await page.getByRole("button", {name: "Add filters"}).click()
    await page.waitForTimeout(1500)
    await shot("s2", {instant: true})

    await page.getByText("State", {exact: true}).last().click()
    await page.waitForTimeout(800)
    const statePanel = page.locator(".edit-popper").first()
    await statePanel.locator("button.group-toggle[aria-label=Failed]").click()
    await page.waitForTimeout(400)
    await statePanel.locator("[aria-label=\"Expand Completed\"]").click()
    await page.waitForTimeout(600)
    await shot("s3", {instant: true})

    await statePanel.getByText("Warning", {exact: true}).first().click()
    await page.waitForTimeout(800)
    await shot("s4", {instant: true})

    await statePanel.locator("button[aria-label=Close]").first().click()
    await shot("s5", {wait: 1500})

    await page.locator(".filter-chip").filter({hasText: "Started"}).first().click()
    await page.waitForTimeout(900)
    await shot("s6", {instant: true})

    const intervalPanel = page.locator(".edit-popper").first()
    await intervalPanel.locator(".kel-select").first().click()
    await page.waitForTimeout(800)
    await shot("s7", {instant: true})

    await visibleDropdownItems(page).filter({hasText: "Last 1 hour"}).first().click()
    await page.waitForTimeout(800)
    const close = intervalPanel.locator("button[aria-label=Close]").first()
    if (await close.isVisible()) {
        await close.click()
    }
    await shot("s8", {wait: 1500})

    await page.getByText("Timeline", {exact: true}).click()
    await shot("s9", {wait: 1500})
}
