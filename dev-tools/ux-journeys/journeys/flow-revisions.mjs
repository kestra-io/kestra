import {settle, visibleDropdownItems} from "../lib.mjs"

export const journey = {
    id: "flow-revisions",
    title: "Comparer et restaurer une révision de flow",
    summary: "Après une modification du flow ingest_events, l'utilisateur compare les révisions 1 et 2 dans un diff côte à côte ou ligne à ligne, restaure la révision 1 (ce qui crée une révision 3) puis consulte le graphe de dépendances du flow.",
    category: "Configuration",
    area: "Flows",
    tags: ["révisions", "diff", "restauration", "dépendances", "subflow"],
    steps: [
        {
            key: "s1",
            file: "01.jpg",
            title: "Flow modifié (révision 2)",
            caption: "L'éditeur de ingest_events montre la version courante : une boucle sur quatre batches, l'appel du subflow process_orders avec batch_size 60 et une nouvelle tâche log_completion. L'en-tête indique 2 révisions.",
            screen: "Flow › Éditeur",
            url: "/ui/main/flows/edit/company.data/ingest_events/edit",
            elements: ["Code editor", "Split view", "Tabs", "Breadcrumb"],
            observation: ""
        },
        {
            key: "s2",
            file: "02.jpg",
            title: "Diff côte à côte",
            caption: "L'onglet Revisions compare directement Revision 1 et Revision 2 (current) côte à côte : la ligne values, batch_size et la tâche ajoutée sont surlignées en rouge et vert. Chaque côté a un sélecteur de révision et un bouton Restore.",
            screen: "Flow › Révisions",
            url: "/ui/main/flows/edit/company.data/ingest_events/revisions?revisionLeft=1&revisionRight=2",
            elements: ["Code editor", "Split view", "Dropdown", "Button", "Tabs"],
            observation: "L'onglet ouvre directement sur la comparaison des deux dernières révisions, sans liste intermédiaire ; l'auteur et la date des révisions ne sont pas visibles."
        },
        {
            key: "s3",
            file: "03.jpg",
            title: "Diff ligne à ligne",
            caption: "Le sélecteur passe de side-by-side à line-by-line : les lignes supprimées et ajoutées s'enchaînent dans une seule colonne avec leurs deux numéros de ligne.",
            screen: "Flow › Révisions",
            url: "/ui/main/flows/edit/company.data/ingest_events/revisions?revisionLeft=1&revisionRight=2",
            elements: ["Code editor", "Dropdown"],
            observation: ""
        },
        {
            key: "s4",
            file: "04.jpg",
            title: "Confirmation de restauration",
            caption: "« Restore » sur la révision 1 ouvre une confirmation « Are you sure you want to restore the revision 1? » avec Cancel et OK.",
            screen: "Dialog › Confirmation",
            url: "/ui/main/flows/edit/company.data/ingest_events/revisions",
            elements: ["Modal", "Button"],
            observation: "La confirmation ne précise pas que la restauration crée une nouvelle révision plutôt que d'écraser l'historique."
        },
        {
            key: "s5",
            file: "05.jpg",
            title: "Révision 3 créée",
            caption: "Après OK, un toast « Successfully saved » apparaît et le diff compare désormais Revision 2 et Revision 3 (current), cette dernière reprenant le contenu de la révision 1.",
            screen: "Flow › Révisions",
            url: "/ui/main/flows/edit/company.data/ingest_events/revisions?revisionLeft=2&revisionRight=3",
            elements: ["Code editor", "Split view", "Toast"],
            observation: ""
        },
        {
            key: "s6",
            file: "06.jpg",
            title: "Graphe de dépendances",
            caption: "L'onglet Dependencies (badge 1) affiche un graphe où ingest_events pointe vers process_orders, avec une recherche, un filtre de namespace et la liste des 2 flows à droite.",
            screen: "Flow › Dépendances",
            url: "/ui/main/flows/edit/company.data/ingest_events/dependencies",
            elements: ["Topology graph", "Search", "Dropdown", "Split view", "Tabs"],
            observation: "Les deux nœuds sont placés aux coins opposés du canevas avec de simples cercles : le graphe occupe tout l'écran pour une seule relation."
        }
    ],
    edges: [
        {from: "s1", to: "s2", label: "Onglet « Revisions »"},
        {from: "s2", to: "s3", label: "Choix de « line-by-line »"},
        {from: "s3", to: "s4", label: "Clic sur « Restore » (révision 1)"},
        {from: "s4", to: "s3", label: "Clic sur « Cancel »"},
        {from: "s4", to: "s5", label: "Clic sur « OK »"},
        {from: "s5", to: "s6", label: "Onglet « Dependencies »"}
    ]
}

export async function capture({page, shot}) {
    await page.goto("/ui/main/flows/edit/company.data/ingest_events/edit")
    await shot("s1", {wait: 2000})

    await page.locator("a[href*=\"/ingest_events/revisions\"]").first().click()
    await shot("s2", {wait: 2000})

    await page.getByText("side-by-side", {exact: true}).first().click()
    await page.waitForTimeout(700)
    await visibleDropdownItems(page).filter({hasText: "line-by-line"}).first().click()
    await shot("s3", {wait: 1200})

    await page.getByRole("button", {name: "Restore"}).first().click()
    await page.waitForTimeout(1000)
    await shot("s4", {instant: true})

    await page.locator(".kel-overlay-dialog, .kel-message-box").filter({visible: true}).getByRole("button", {name: "OK"}).click()
    await page.waitForTimeout(1500)
    await shot("s5", {instant: true, toasts: true})

    await settle(page, 1000)
    await page.locator("a[href*=\"/ingest_events/dependencies\"]").first().click()
    await shot("s6", {wait: 2500})
}
