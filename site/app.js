import {
  BRANCH, REPOSITORY, choosePreviews, collectionsFromTree, encodePath,
  formatFileSize, formatFolderDate,
} from './catalog.js';
import { createFolderZip } from './download.js';

const elements = {
  list: document.querySelector('#collection-list'),
  search: document.querySelector('#collection-search'),
  count: document.querySelector('#collection-count'),
  visibleCount: document.querySelector('#visible-count'),
  empty: document.querySelector('#empty-state'),
  syncStatus: document.querySelector('#sync-status'),
  heroCollections: document.querySelector('#hero-collection-count'),
  heroCollectionLabel: document.querySelector('#hero-collection-label'),
  heroImages: document.querySelector('#hero-image-count'),
  heroGallery: document.querySelector('#hero-gallery'),
};

const treeUrl = `https://api.github.com/repos/${REPOSITORY}/git/trees/${encodeURIComponent(BRANCH)}?recursive=1`;
let cards = [];

function assetUrl(path) {
  return new URL(`./${encodePath(path)}`, document.baseURI).href;
}

function plural(number, singular, pluralForm = `${singular}s`) {
  return `${number.toLocaleString('en')} ${number === 1 ? singular : pluralForm}`;
}

function updateHero(collections) {
  const images = collections.reduce((count, collection) => count + collection.images.length, 0);
  elements.heroCollections.textContent = String(collections.length).padStart(2, '0');
  elements.heroCollectionLabel.textContent = collections.length === 1 ? 'COLLECTION' : 'COLLECTIONS';
  elements.heroImages.textContent = images.toLocaleString('en');

  const latestWithImages = collections.find(collection => collection.previews.length);
  if (!latestWithImages) return;
  // Keep a deliberately selected, lightweight set for the existing collection.
  const chosenNames = ['04-angelina-on-stage.jpg', '15-i-love-you-more.jpg', '21-ill-love-you-always.jpg'];
  const curated = latestWithImages.name === '7th Nov Day'
    ? chosenNames.map(name => latestWithImages.previews.find(file => file.relativePath === name)).filter(Boolean)
    : [];
  const previews = curated.length === 3 ? curated : choosePreviews(latestWithImages, 3);
  const photos = [...elements.heroGallery.querySelectorAll('.hero-photo')];
  elements.heroGallery.dataset.count = String(previews.length);
  photos.forEach((photo, index) => {
    const file = previews[index];
    photo.hidden = !file;
    if (file) photo.querySelector('img').src = assetUrl(file.path);
  });
}

function makeElement(tag, className, text) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text !== undefined) element.textContent = text;
  return element;
}

function makeCard(collection, index) {
  const card = makeElement('article', 'collection-card');
  card.dataset.search = `${collection.name} ${formatFolderDate(collection.date)}`.toLocaleLowerCase();

  const art = makeElement('div', 'card-art');
  art.setAttribute('aria-hidden', 'true');
  const previews = choosePreviews(collection, 4);
  if (previews.length) {
    const grid = makeElement('div', 'card-image-grid');
    grid.dataset.count = String(previews.length);
    previews.forEach(file => {
      const frame = makeElement('div', 'card-image');
      const image = document.createElement('img');
      image.src = assetUrl(file.path);
      image.alt = '';
      image.loading = 'lazy';
      image.decoding = 'async';
      frame.append(image);
      grid.append(frame);
    });
    art.append(grid);
  } else {
    const placeholder = makeElement('div', 'card-art-empty');
    placeholder.append(makeElement('span', '', '✳'), document.createTextNode('A COLLECTION TO EXPLORE'));
    art.append(placeholder);
  }
  const caption = makeElement('div', 'card-art-caption');
  caption.append(makeElement('span', '', 'A LITTLE LOOK INSIDE'), makeElement('span', '', `${String(previews.length).padStart(2, '0')} / ${String(collection.images.length).padStart(2, '0')} IMAGES`));
  art.append(caption);

  const body = makeElement('div', 'card-body');
  const top = makeElement('div', 'card-topline');
  const sequence = makeElement('span', '');
  sequence.append(makeElement('strong', '', String(index + 1).padStart(2, '0')), document.createTextNode(' / COLLECTION'));
  top.append(sequence);
  if (index === 0 && collection.date) {
    const badge = makeElement('span', 'card-badge');
    badge.append(makeElement('span', '', '✳'), document.createTextNode(' LATEST COLLECTION'));
    top.append(badge);
  }
  const date = makeElement('p', 'card-date', formatFolderDate(collection.date));
  const title = makeElement('h3', 'card-title', collection.name);
  const description = makeElement('p', 'card-description', 'A little collection, ready to take with you.');
  const stats = makeElement('div', 'card-stats');
  const details = [plural(collection.images.length, 'image'), plural(collection.files.length, 'file'), formatFileSize(collection.bytes)].filter(Boolean);
  details.forEach((detail, detailIndex) => {
    if (detailIndex) stats.append(makeElement('span', 'card-stat-separator'));
    stats.append(makeElement('span', 'card-stat', detail));
  });

  const actions = makeElement('div', 'card-actions');
  const download = makeElement('button', 'button button-dark download-button');
  download.type = 'button';
  // Static icon only; names from the repository are inserted with textContent.
  download.innerHTML = '<svg class="download-icon" aria-hidden="true" viewBox="0 0 24 24"><path d="M12 3v12m-4-4 4 4 4-4M5 17v3h14v-3"></path></svg><span class="button-label">Download folder</span>';
  download.append(makeElement('span', 'visually-hidden', ` ${collection.name} as a ZIP`));
  const githubLink = makeElement('a', 'card-link');
  githubLink.href = `https://github.com/${REPOSITORY}/tree/${encodeURIComponent(BRANCH)}/${encodePath(collection.name)}`;
  githubLink.target = '_blank';
  githubLink.rel = 'noopener noreferrer';
  const externalArrow = makeElement('span', '', '↗');
  externalArrow.setAttribute('aria-hidden', 'true');
  githubLink.append(document.createTextNode('View folder '), externalArrow, makeElement('span', 'visually-hidden', ` ${collection.name} on GitHub`));
  const status = makeElement('p', 'download-status');
  status.setAttribute('role', 'status');
  download.addEventListener('click', () => downloadCollection(collection, download, status));
  actions.append(download, githubLink);
  body.append(top, date, title, description, stats, actions, status);
  card.append(art, body);
  return card;
}

async function downloadCollection(collection, button, status) {
  const label = button.querySelector('.button-label');
  button.disabled = true;
  status.classList.remove('is-error');
  status.textContent = 'Getting your files ready. Please keep this page open.';
  label.textContent = 'Preparing…';
  try {
    const blob = await createFolderZip(collection, {
      JSZip: window.JSZip,
      fetchFile: (path, options) => fetch(assetUrl(path), options),
      onProgress(progress) {
        if (progress.phase === 'fetch') {
          label.textContent = `Preparing ${progress.completed}/${progress.total}`;
          status.textContent = `Gathering files ${progress.completed} of ${progress.total}…`;
        } else {
          label.textContent = 'Packing ZIP…';
          status.textContent = `Packing your folder… ${progress.percent}%`;
        }
      },
    });
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = `${collection.name.replace(/[<>:"/\\|?*]/g, '_').trim() || 'collection'}.zip`;
    document.body.append(link);
    link.click();
    link.remove();
    // Browsers can start large downloads asynchronously, so don't revoke immediately.
    setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
    status.textContent = 'Your download is ready.';
  } catch (error) {
    console.error('Collection download failed:', error);
    status.classList.add('is-error');
    status.textContent = 'We couldn’t prepare this ZIP. Please try again, or use “View folder” to open the files on GitHub.';
  } finally {
    button.disabled = false;
    label.textContent = 'Download folder';
  }
}

function applySearch() {
  const query = elements.search.value.trim().toLocaleLowerCase();
  let visible = 0;
  for (const { card } of cards) {
    const show = card.dataset.search.includes(query);
    card.hidden = !show;
    card.classList.toggle('is-featured', show && visible === 0);
    if (show) visible += 1;
  }
  elements.empty.hidden = visible !== 0 || cards.length === 0;
  elements.count.textContent = query ? 'MATCHING COLLECTIONS' : 'ALL COLLECTIONS';
  elements.visibleCount.textContent = `/ ${String(visible).padStart(2, '0')}`;
}

function renderCollections(collections) {
  updateHero(collections);
  elements.list.replaceChildren();
  cards = collections.map((collection, index) => ({ card: makeCard(collection, index), collection }));
  cards.forEach(({ card }) => elements.list.append(card));
  elements.list.setAttribute('aria-busy', 'false');
  if (!collections.length) {
    const message = makeElement('div', 'loading-state', 'No collections yet. New folders will appear here when they are added.');
    elements.list.append(message);
  }
  applySearch();
}

function showSyncMessage(message) {
  elements.syncStatus.replaceChildren(document.createTextNode(message));
  const retry = makeElement('button', '', 'Try again');
  retry.type = 'button';
  retry.addEventListener('click', loadCollections, { once: true });
  elements.syncStatus.append(retry);
  elements.syncStatus.hidden = false;
}

async function getTree(url, options = {}) {
  const response = await fetch(url, options);
  if (!response.ok) throw new Error(`Catalog request failed (HTTP ${response.status}).`);
  const data = await response.json();
  if (!Array.isArray(data.tree) || data.truncated) throw new Error('The catalog response was incomplete.');
  return data.tree;
}

async function loadCollections() {
  elements.syncStatus.hidden = true;
  elements.list.setAttribute('aria-busy', 'true');
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10_000);
  try {
    const tree = await getTree(treeUrl, { signal: controller.signal });
    renderCollections(collectionsFromTree(tree));
  } catch (error) {
    console.warn('Live catalog unavailable:', error);
    try {
      const tree = await getTree(new URL('./site/snapshot.json', document.baseURI));
      renderCollections(collectionsFromTree(tree));
      showSyncMessage('Showing a saved snapshot. Newer collections may be missing until the connection is restored.');
    } catch (snapshotError) {
      console.error('Saved catalog unavailable:', snapshotError);
      elements.list.replaceChildren();
      elements.list.setAttribute('aria-busy', 'false');
      showSyncMessage('We couldn’t load the collections right now.');
    }
  } finally {
    clearTimeout(timeout);
  }
}

elements.search.addEventListener('input', applySearch);
document.addEventListener('keydown', event => {
  if (event.key !== '/' || event.ctrlKey || event.metaKey || event.altKey) return;
  const target = event.target;
  if (target instanceof HTMLElement && (target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName))) return;
  event.preventDefault();
  elements.search.focus();
});

loadCollections();
