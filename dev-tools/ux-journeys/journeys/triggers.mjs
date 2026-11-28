export const journey = {
    id: "triggers",
    title: "Gérer les triggers planifiés",
    summary: "L'utilisateur part d'un flow déclarant un trigger Schedule, consulte l'onglet Triggers du flow puis la page globale Triggers (catalogue Add et liste Manage). Il désactive un trigger d'un clic et ouvre le dialogue de backfill d'un autre.",
    category: "Configuration",
    area: "Triggers",
    tags: ["schedule", "cron", "trigger", "désactiver", "backfill"],
    steps: [
        {
            key: "s1",
            file: "01.jpg",
            title: "Trigger déclaré dans le YAML",
            caption: "Dans l'éditeur de daily_sales_report, le bloc triggers déclare every_morning de type Schedule avec le cron « 0 7 * * * » ; le panneau Docs est ouvert à droite.",
            screen: "Flow › Éditeur",
            url: "/ui/main/flows/edit/company.data/daily_sales_report/edit",
            elements: ["Code editor", "Split view", "Tabs", "Breadcrumb"],
            observation: ""
        },
        {
            key: "s2",
            file: "02.jpg",
            title: "Onglet Triggers du flow",
            caption: "L'onglet Triggers liste every_morning avec son type, la dernière exécution (7:00 AM), la prochaine évaluation (le lendemain 7:00 AM), un bouton « Backfill executions » et un interrupteur Enabled. Un lien « Add a trigger » est proposé dessous.",
            screen: "Flow › Triggers",
            url: "/ui/main/flows/edit/company.data/daily_sales_report/triggers",
            elements: ["Table", "Filter bar", "Button", "Tabs"],
            observation: "La date de prochaine évaluation répond directement à la question « quand tournera-t-il ? »."
        },
        {
            key: "s3",
            file: "03.jpg",
            title: "Page Triggers : catalogue",
            caption: "La page Triggers de la section Tenant s'ouvre sur l'onglet « Add » : un catalogue de cartes (Flow, HTTP, McpToolTrigger, Schedule, ScheduleOnDates, Webhook) filtrable par All, Core, Realtime, App, chacune avec un bouton Add.",
            screen: "Triggers › Catalogue",
            url: "/ui/main/admin/triggers/add",
            elements: ["Card", "Search", "Tag", "Button", "Tabs", "Sidebar"],
            observation: "La page globale s'ouvre sur l'ajout plutôt que sur la liste des triggers existants : il faut un clic de plus pour voir l'état des 4 triggers en place."
        },
        {
            key: "s4",
            file: "04.jpg",
            title: "Liste Manage",
            caption: "L'onglet « Manage » liste les 4 triggers (every_morning, every_six_hours, hourly_sync, weekly_monday) avec flow, namespace, dernier déclenchement, Backfill et Enabled ; hourly_sync est désactivé et son bouton Backfill grisé. Un export CSV est disponible.",
            screen: "Triggers › Liste",
            url: "/ui/main/admin/triggers/manage",
            elements: ["Table", "Filter bar", "Button", "Tabs"],
            observation: "La colonne « Next evaluation date » visible dans l'onglet du flow n'apparaît pas dans la liste globale."
        },
        {
            key: "s5",
            file: "05.jpg",
            title: "Trigger désactivé",
            caption: "Un clic sur l'interrupteur de weekly_monday le désactive immédiatement, grise son bouton Backfill et affiche le toast « Successfully saved – weekly_monday is successfully saved ».",
            screen: "Triggers › Liste",
            url: "/ui/main/admin/triggers/manage",
            elements: ["Table", "Toast"],
            observation: "Aucune confirmation n'est demandée avant de couper un planning de production ; le toast parle de « saved » et non de « disabled »."
        },
        {
            key: "s6",
            file: "06.jpg",
            title: "Dialogue de backfill",
            caption: "« Backfill executions » sur every_morning ouvre une modale avec Start date et End date, les onglets Inputs / Other properties / cURL command (« This flow has no inputs. ») et le bouton « Execute backfill », désactivé tant que les dates sont vides.",
            screen: "Dialog › Backfill",
            url: "/ui/main/admin/triggers/manage",
            elements: ["Modal", "Date picker", "Tabs", "Button"],
            observation: "Le dialogue reprend la structure du dialogue d'exécution (inputs, cURL), ce qui le rend familier."
        }
    ],
    edges: [
        {from: "s1", to: "s2", label: "Onglet « Triggers »"},
        {from: "s2", to: "s3", label: "Sidebar Tenant › « Triggers »"},
        {from: "s3", to: "s4", label: "Onglet « Manage »"},
        {from: "s4", to: "s5", label: "Clic sur l'interrupteur Enabled"},
        {from: "s4", to: "s6", label: "Clic sur « Backfill executions »"}
    ]
}

export async function capture({page, shot}) {
    await page.goto("/ui/main/flows/edit/company.data/daily_sales_report/edit")
    await shot("s1", {wait: 2500})

    await page.locator("a[href$=\"/daily_sales_report/triggers\"]").first().click()
    await shot("s2", {wait: 1500})

    await page.goto("/ui/main/admin/triggers")
    await shot("s3", {wait: 1500})

    await page.getByText("Manage", {exact: true}).first().click()
    await shot("s4", {wait: 1800})

    await page.locator("tr").filter({hasText: "weekly_monday"}).first().locator(".kel-switch").click()
    await page.waitForTimeout(1200)
    await shot("s5", {instant: true, toasts: true})

    await page.locator("tr").filter({hasText: "every_morning"}).first().getByText("Backfill executions").click()
    await page.waitForTimeout(1500)
    await shot("s6", {instant: true})
}
