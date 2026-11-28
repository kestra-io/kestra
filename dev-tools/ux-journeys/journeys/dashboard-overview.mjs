export const journey = {
    id: "dashboard-overview",
    title: "Dashboard et structure de navigation",
    summary: "L'utilisateur part du dashboard alimenté par une trentaine d'exécutions et explore la sidebar : ses trois sections repliables (Workspace, Resources, Tenant), une entrée Enterprise verrouillée, l'accès Admin et le masquage du menu.",
    category: "Navigation",
    area: "Dashboard",
    tags: ["dashboard", "sidebar", "navigation", "kpi", "enterprise"],
    steps: [
        {
            key: "s1",
            file: "01.jpg",
            title: "Default Dashboard",
            caption: "Le Default Dashboard affiche quatre KPI (Success Ratio 71,43 %, Failed Ratio 14,29 %, In Progress 2, Pending 0), un histogramme « Executions over time », un donut de 35 exécutions et un tableau des exécutions filtrable par état. La sidebar n'a que la section Workspace ouverte.",
            screen: "Dashboard",
            url: "/ui/main/dashboards",
            elements: ["Chart", "Card", "Table", "Status badge", "Tabs", "Filter bar", "Sidebar", "Button"],
            observation: "« In Progress 2 – running now » compte les deux exécutions en pause (approval_workflow), alors que le tableau les classe sous Paused et que Running est vide."
        },
        {
            key: "s2",
            file: "02.jpg",
            title: "Sidebar dépliée",
            caption: "Resources (Namespaces, Assets, Cases, Plugins, Blueprints) et Tenant (System Overview, Policies, KV Store, Secrets, Credentials, Triggers…) sont dépliées ; les entrées Enterprise portent un cadenas grisé.",
            screen: "Dashboard",
            url: "/ui/main/dashboards",
            elements: ["Sidebar", "Chart", "Card"],
            observation: "Avec les trois sections ouvertes, la sidebar dépasse la hauteur de l'écran et une part importante des entrées sont verrouillées en OSS."
        },
        {
            key: "s3",
            file: "03.jpg",
            title: "Entrée verrouillée : Apps",
            caption: "Un clic sur Apps (cadenas) ouvre une page de présentation « Build custom Apps with Kestra » décrivant la fonctionnalité Enterprise, avec « Contact Sales » et « Learn more ».",
            screen: "Apps › Upsell",
            url: "/ui/main/apps",
            elements: ["Empty state", "Button", "Sidebar"],
            observation: "L'entrée verrouillée mène à une explication plutôt qu'à une erreur, ce qui est clair ; le titre de l'onglet devient « Build custom Apps with Kestra »."
        },
        {
            key: "s4",
            file: "04.jpg",
            title: "Accès Admin",
            caption: "Le lien « Admin » en bas de la sidebar ouvre directement la page Settings (Main Configuration › Preferences).",
            screen: "Paramètres",
            url: "/ui/main/preferences",
            elements: ["Sidebar", "Form", "Dropdown"],
            observation: "Le libellé « Admin » mène à des préférences personnelles (éditeur, thème, onglets par défaut) : le nom ne correspond pas au contenu."
        },
        {
            key: "s5",
            file: "05.jpg",
            title: "Menu masqué",
            caption: "Le bouton de bascule du menu masque entièrement la sidebar : la liste des flows occupe toute la largeur et une icône hamburger apparaît à gauche du logo.",
            screen: "Flows › Liste",
            url: "/ui/main/flows",
            elements: ["Table", "Filter bar", "Status badge", "Tag", "Button"],
            observation: ""
        }
    ],
    edges: [
        {from: "s1", to: "s2", label: "Dépliage de « Resources » et « Tenant »"},
        {from: "s2", to: "s3", label: "Clic sur « Apps »"},
        {from: "s3", to: "s4", label: "Clic sur « Admin »"},
        {from: "s4", to: "s5", label: "Bascule du menu puis « Flows »"}
    ]
}

function sidebarSection(page, title) {
    return page.locator("button.ks-sidebar-section__title").filter({hasText: title})
}

export async function capture({page, shot}) {
    await page.goto("/ui/main/dashboards")
    await shot("s1", {wait: 2500})

    await sidebarSection(page, "Resources").click()
    await page.waitForTimeout(500)
    await sidebarSection(page, "Tenant").click()
    await page.waitForTimeout(700)
    await shot("s2", {instant: true})

    await page.locator("a[href=\"/ui/main/apps\"]").first().click()
    await shot("s3", {wait: 1500})

    await page.getByText("Admin", {exact: true}).last().click()
    await page.waitForTimeout(800)
    await shot("s4", {instant: true})

    await page.getByRole("button", {name: "Toggle menu"}).first().click()
    await page.waitForTimeout(800)
    await page.goto("/ui/main/flows")
    await shot("s5", {wait: 1500})
}
