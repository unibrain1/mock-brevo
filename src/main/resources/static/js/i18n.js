// UI strings for the admin page (FR / EN).
//
// Loaded before app.js. Exposes window.I18N:
//   t(key, vars)   → translated string, `{name}` placeholders filled from vars
//   lang()         → current language code ('fr' | 'en')
//   setLang(code)  → persist + switch language, re-apply static markup
//   applyStatic()  → translate every [data-i18n*] element in the page
//
// Static markup is tagged with:
//   data-i18n="key"             → textContent
//   data-i18n-html="key"        → innerHTML (trusted strings from this file only)
//   data-i18n-title="key"       → title attribute
//   data-i18n-placeholder="key" → placeholder attribute
//
// Language: localStorage override, else browser language (fr* → fr, else en).
(() => {
  'use strict';

  const LANG_KEY = 'mock-brevo-lang';
  const SUPPORTED = ['fr', 'en'];

  const STRINGS = {
    fr: {
      'lang.toggleTitle': 'Changer de langue (FR / EN)',
      'theme.toggleTitle': 'Basculer thème clair / sombre',
      'theme.light': 'clair',
      'theme.dark': 'sombre',

      'tab.accounts': 'Comptes',
      'tab.requests': 'Appels REST',
      'nav.filterPlaceholder': 'Filtrer (clé API, chemin…)',

      'common.loading': 'Chargement…',
      'common.error': 'Erreur',
      'common.errorPrefix': 'Erreur : {msg}',
      'common.masked': 'masquée',
      'common.maskedKey': 'clé masquée',
      'common.unnamed': '(sans nom)',
      'common.none': 'aucun',
      'common.never': 'jamais',
      'common.home': 'Accueil',
      'common.backHome': "Retour à l'accueil",
      'common.mainUi': 'UI principale',
      'common.usefulLinks': 'Liens utiles',
      'common.brevoDocs': 'Documentation Brevo',
      'common.account': 'Compte',

      'time.justNow': "à l'instant",
      'time.seconds': '{n} s',
      'time.minutes': '{n} min',
      'time.hours': '{n} h',
      'time.days': '{n} j',

      'accounts.hintHtml':
        'Envoyez un appel vers <code>http://localhost:8080/v3/account</code> avec un header ' +
        '<code>api-key: &lt;votre-clé&gt;</code> pour provisionner un compte. ' +
        'Chaque nouveau compte reçoit automatiquement <strong>2 campagnes par défaut</strong>.',
      'accounts.resetTitle':
        'Supprimer tous les comptes, contacts, listes, campagnes, templates, dossiers, expéditeurs et emails — irréversible',
      'accounts.resetLabel': 'Tout réinitialiser',
      'accounts.col.apiKey': 'Clé API',
      'accounts.col.identity': 'Identité',
      'accounts.col.counters': 'Compteurs',
      'accounts.col.created': 'Créé',
      'accounts.col.lastSeen': 'Vu dernier',
      'accounts.col.actions': 'Actions',
      'accounts.empty': 'Aucun compte — envoyez un appel pour provisionner.',
      'accounts.show': 'Afficher {what}',
      'accounts.newCampaignTitle': 'Créer une nouvelle campagne',
      'accounts.newCampaign': '+ Campagne',
      'accounts.health': '{n} compte(s) · {time}',

      'drill.noCampaigns': 'Aucune campagne.',
      'drill.campaignsTitle': 'Campagnes ({n}) — cliquer sur le nom pour ouvrir la vue détail',
      'drill.col.id': 'ID',
      'drill.col.name': 'Nom',
      'drill.col.subject': 'Sujet',
      'drill.col.status': 'Statut',
      'drill.col.delivered': 'Delivered',
      'drill.col.utm': 'UTM',
      'drill.col.sent': 'Envoyée',
      'drill.neverSent': 'jamais envoyée',
      'drill.noLists': 'Aucune liste.',
      'drill.listsTitle': 'Listes ({n}) — cliquer sur le nom pour ouvrir la vue détail',
      'drill.col.contacts': 'Contacts',
      'drill.col.folder': 'Dossier',

      'requests.hintHtml':
        'Buffer mémoire des derniers appels sur <code>/v3/**</code> et <code>/mock-webhooks/**</code> ' +
        '(limite 500). Cliquez une ligne pour voir headers et body.',
      'requests.col.time': 'Horodatage',
      'requests.col.method': 'Méthode',
      'requests.col.path': 'Chemin',
      'requests.col.apiKey': 'Clé API',
      'requests.col.status': 'Status',
      'requests.col.duration': 'Durée',
      'requests.col.size': 'Taille',
      'requests.emptyHtml': 'Aucun appel capté. Envoyez une requête vers <code>/v3/…</code>.',
      'requests.health': '{n} en buffer · {time}',
      'requests.loadingDetail': 'Chargement du détail…',
      'requests.noHeaders': 'Aucun header.',
      'requests.noBody': 'Aucun body.',
      'requests.truncated': 'tronqué à 16 KiB',
      'requests.copyTitle': 'Copier dans le presse-papier',
      'requests.copy': 'Copier',
      'requests.copied': 'Copié ✓',
      'requests.loadingEditor': "Chargement de l'éditeur…",
      'requests.request': 'Requête',
      'requests.response': 'Réponse',

      'health.error': 'erreur: {msg}',
      'health.reset': 'réinitialisé · {summary}',
      'health.resetError': 'erreur reset: {msg}',

      'modal.newCampaign': 'Nouvelle campagne',
      'modal.name': 'Nom',
      'modal.namePlaceholder': 'Newsletter janvier 2026',
      'modal.nameHint': 'laissé vide → nom aléatoire',
      'modal.subject': 'Sujet',
      'modal.subjectPlaceholder': 'Vos dernières actualités',
      'modal.subjectHint': 'laissé vide → sujet aléatoire',
      'modal.status': 'Statut',
      'modal.statusSent': 'sent (avec stats générées)',
      'modal.statusDraft': 'draft (stats nulles)',
      'modal.utm': 'UTM campaign',
      'modal.utmPlaceholder': 'campagne-2026-q2',
      'modal.lists': 'Listes (IDs CSV)',
      'modal.listsPlaceholder': 'ex: 1,2',
      'modal.listsHint': 'optionnel — sépare les IDs par une virgule',
      'modal.cancel': 'Annuler',
      'modal.create': 'Créer',
      'modal.creating': 'Création…',

      'deep.noSubject': '(pas de sujet)',
      'deep.sender': 'Expéditeur',
      'deep.replyTo': 'Reply-to',
      'deep.template': 'Template',
      'deep.utm': 'UTM',
      'deep.targetLists': 'Listes cibles',
      'deep.created': 'Créée',
      'deep.sent': 'Envoyée',
      'deep.stats': 'Statistiques',
      'deep.contactsCount': '{n} contact(s)',
      'deep.folder': 'Dossier',
      'deep.contactsTitle': 'Contacts ({n})',
      'deep.col.name': 'Nom',
      'deep.emptyList': 'Liste vide.',
      'deep.notFound': '{what} #{id} introuvable',
      'deep.notFoundHint': "Ce compte n'a peut-être pas été provisionné dans ce mock, ou l'ID est périmé.",
      'deep.campaign': 'Campagne',
      'deep.campaignTitle': 'Campagne #{id}',
      'deep.campaignPrefix': 'Campagne',
      'deep.list': 'Liste',
      'deep.listTitle': 'Liste de contacts #{id}',
      'deep.listPrefix': 'Liste',

      'reset.confirm':
        'Supprimer définitivement TOUS les comptes, contacts, listes, campagnes, templates, dossiers, expéditeurs et emails ?\n\nCette action est irréversible.',
      'reset.running': 'Réinitialisation…',
    },

    en: {
      'lang.toggleTitle': 'Switch language (FR / EN)',
      'theme.toggleTitle': 'Toggle light / dark theme',
      'theme.light': 'light',
      'theme.dark': 'dark',

      'tab.accounts': 'Accounts',
      'tab.requests': 'REST calls',
      'nav.filterPlaceholder': 'Filter (API key, path…)',

      'common.loading': 'Loading…',
      'common.error': 'Error',
      'common.errorPrefix': 'Error: {msg}',
      'common.masked': 'masked',
      'common.maskedKey': 'masked key',
      'common.unnamed': '(unnamed)',
      'common.none': 'none',
      'common.never': 'never',
      'common.home': 'Home',
      'common.backHome': 'Back to home',
      'common.mainUi': 'Main UI',
      'common.usefulLinks': 'Useful links',
      'common.brevoDocs': 'Brevo documentation',
      'common.account': 'Account',

      'time.justNow': 'just now',
      'time.seconds': '{n} s',
      'time.minutes': '{n} min',
      'time.hours': '{n} h',
      'time.days': '{n} d',

      'accounts.hintHtml':
        'Send a request to <code>http://localhost:8080/v3/account</code> with an ' +
        '<code>api-key: &lt;your-key&gt;</code> header to provision an account. ' +
        'Every new account automatically gets <strong>2 default campaigns</strong>.',
      'accounts.resetTitle':
        'Delete all accounts, contacts, lists, campaigns, templates, folders, senders and emails — cannot be undone',
      'accounts.resetLabel': 'Reset everything',
      'accounts.col.apiKey': 'API key',
      'accounts.col.identity': 'Identity',
      'accounts.col.counters': 'Counters',
      'accounts.col.created': 'Created',
      'accounts.col.lastSeen': 'Last seen',
      'accounts.col.actions': 'Actions',
      'accounts.empty': 'No accounts yet — send a request to provision one.',
      'accounts.show': 'Show {what}',
      'accounts.newCampaignTitle': 'Create a new campaign',
      'accounts.newCampaign': '+ Campaign',
      'accounts.health': '{n} account(s) · {time}',

      'drill.noCampaigns': 'No campaigns.',
      'drill.campaignsTitle': 'Campaigns ({n}) — click a name to open the detail view',
      'drill.col.id': 'ID',
      'drill.col.name': 'Name',
      'drill.col.subject': 'Subject',
      'drill.col.status': 'Status',
      'drill.col.delivered': 'Delivered',
      'drill.col.utm': 'UTM',
      'drill.col.sent': 'Sent',
      'drill.neverSent': 'never sent',
      'drill.noLists': 'No lists.',
      'drill.listsTitle': 'Lists ({n}) — click a name to open the detail view',
      'drill.col.contacts': 'Contacts',
      'drill.col.folder': 'Folder',

      'requests.hintHtml':
        'In-memory buffer of the latest calls to <code>/v3/**</code> and <code>/mock-webhooks/**</code> ' +
        '(limit 500). Click a row to see headers and body.',
      'requests.col.time': 'Timestamp',
      'requests.col.method': 'Method',
      'requests.col.path': 'Path',
      'requests.col.apiKey': 'API key',
      'requests.col.status': 'Status',
      'requests.col.duration': 'Duration',
      'requests.col.size': 'Size',
      'requests.emptyHtml': 'No calls captured yet. Send a request to <code>/v3/…</code>.',
      'requests.health': '{n} in buffer · {time}',
      'requests.loadingDetail': 'Loading detail…',
      'requests.noHeaders': 'No headers.',
      'requests.noBody': 'No body.',
      'requests.truncated': 'truncated at 16 KiB',
      'requests.copyTitle': 'Copy to clipboard',
      'requests.copy': 'Copy',
      'requests.copied': 'Copied ✓',
      'requests.loadingEditor': 'Loading editor…',
      'requests.request': 'Request',
      'requests.response': 'Response',

      'health.error': 'error: {msg}',
      'health.reset': 'reset · {summary}',
      'health.resetError': 'reset error: {msg}',

      'modal.newCampaign': 'New campaign',
      'modal.name': 'Name',
      'modal.namePlaceholder': 'January 2026 newsletter',
      'modal.nameHint': 'leave empty → random name',
      'modal.subject': 'Subject',
      'modal.subjectPlaceholder': 'Your latest news',
      'modal.subjectHint': 'leave empty → random subject',
      'modal.status': 'Status',
      'modal.statusSent': 'sent (with generated stats)',
      'modal.statusDraft': 'draft (zero stats)',
      'modal.utm': 'UTM campaign',
      'modal.utmPlaceholder': 'campaign-2026-q2',
      'modal.lists': 'Lists (CSV IDs)',
      'modal.listsPlaceholder': 'e.g. 1,2',
      'modal.listsHint': 'optional — separate IDs with commas',
      'modal.cancel': 'Cancel',
      'modal.create': 'Create',
      'modal.creating': 'Creating…',

      'deep.noSubject': '(no subject)',
      'deep.sender': 'Sender',
      'deep.replyTo': 'Reply-to',
      'deep.template': 'Template',
      'deep.utm': 'UTM',
      'deep.targetLists': 'Target lists',
      'deep.created': 'Created',
      'deep.sent': 'Sent',
      'deep.stats': 'Statistics',
      'deep.contactsCount': '{n} contact(s)',
      'deep.folder': 'Folder',
      'deep.contactsTitle': 'Contacts ({n})',
      'deep.col.name': 'Name',
      'deep.emptyList': 'Empty list.',
      'deep.notFound': '{what} #{id} not found',
      'deep.notFoundHint': 'This account may not have been provisioned in this mock, or the ID is stale.',
      'deep.campaign': 'Campaign',
      'deep.campaignTitle': 'Campaign #{id}',
      'deep.campaignPrefix': 'Campaign',
      'deep.list': 'List',
      'deep.listTitle': 'Contact list #{id}',
      'deep.listPrefix': 'List',

      'reset.confirm':
        'Permanently delete ALL accounts, contacts, lists, campaigns, templates, folders, senders and emails?\n\nThis cannot be undone.',
      'reset.running': 'Resetting…',
    },
  };

  const detect = () => {
    try {
      const saved = localStorage.getItem(LANG_KEY);
      if (SUPPORTED.includes(saved)) return saved;
    } catch (e) {}
    const nav = (navigator.language || '').toLowerCase();
    return nav.startsWith('fr') ? 'fr' : 'en';
  };

  let current = detect();

  const t = (key, vars) => {
    const s = STRINGS[current][key] ?? STRINGS.fr[key] ?? key;
    if (!vars) return s;
    return s.replace(/\{(\w+)\}/g, (m, k) => (k in vars ? String(vars[k]) : m));
  };

  const applyStatic = (root = document) => {
    document.documentElement.setAttribute('lang', current);
    root.querySelectorAll('[data-i18n]').forEach(el => { el.textContent = t(el.dataset.i18n); });
    root.querySelectorAll('[data-i18n-html]').forEach(el => { el.innerHTML = t(el.dataset.i18nHtml); });
    root.querySelectorAll('[data-i18n-title]').forEach(el => { el.title = t(el.dataset.i18nTitle); });
    root.querySelectorAll('[data-i18n-placeholder]').forEach(el => { el.placeholder = t(el.dataset.i18nPlaceholder); });
  };

  const setLang = (code) => {
    if (!SUPPORTED.includes(code)) return;
    current = code;
    try { localStorage.setItem(LANG_KEY, code); } catch (e) {}
    applyStatic();
  };

  window.I18N = { t, lang: () => current, setLang, applyStatic, SUPPORTED };
})();
