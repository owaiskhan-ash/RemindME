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
  folderTotal: document.querySelector('#folder-total'),
  empty: document.querySelector('#empty-state'),
  syncStatus: document.querySelector('#sync-status'),
  dialog: document.querySelector('#preview-dialog'),
  closePreview: document.querySelector('#close-preview'),
  previewTitle: document.querySelector('#preview-title'),
  previewSummary: document.querySelector('#preview-summary'),
  previewContent: document.querySelector('#preview-content'),
};

const treeUrl = `https://api.github.com/repos/${REPOSITORY}/git/trees/${encodeURIComponent(BRANCH)}?recursive=1`;
const icons = {
  folder: '<svg aria-hidden="true" viewBox="0 0 24 24" fill="none"><path d="M3.5 6.5h6l2 2h9v9.5a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z"></path></svg>',
  external: '<svg aria-hidden="true" viewBox="0 0 24 24" fill="none"><path d="M8 5H5v14h14v-3M13 5h6v6M19 5l-9 9"></path></svg>',
  eye: '<svg aria-hidden="true" viewBox="0 0 24 24" fill="none"><path d="M2.5 12s3.4-5.5 9.5-5.5 9.5 5.5 9.5 5.5-3.4 5.5-9.5 5.5S2.5 12 2.5 12z"></path><circle cx="12" cy="12" r="2.5"></circle></svg>',
  download: '<svg aria-hidden="true" viewBox="0 0 24 24" fill="none"><path d="M12 3v12m-4-4 4 4 4-4M5 17v3h14v-3"></path></svg>',
  file: '<svg aria-hidden="true" viewBox="0 0 24 24" fill="none"><path d="M6 3.5h8l4 4V20H6zM14 3.5v4h4"></path></svg>',
};
let cards = [];
let sceneSeed = 0;

const SCENE_STARS = 26;
const SCENE_CLOUDS = 5;

function mulberry32(seed) {
  return () => {
    seed = (seed + 0x6d2b79f5) | 0;
    let t = seed;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function makeStarField() {
  const random = mulberry32((sceneSeed = (sceneSeed + 1) | 0) * 7919 + 13);
  const field = makeElement('span', 'card-stars');
  field.setAttribute('aria-hidden', 'true');
  for (let index = 0; index < SCENE_STARS; index += 1) {
    const star = makeElement('i', 'card-star');
    const size = (random() * 1.7 + 0.85).toFixed(2);
    star.style.setProperty('--x', `${(random() * 100).toFixed(2)}%`);
    star.style.setProperty('--y', `${(random() * 100).toFixed(2)}%`);
    star.style.setProperty('--size', `${size}px`);
    star.style.setProperty('--delay', `${(random() * 5.5).toFixed(2)}s`);
    star.style.setProperty('--duration', `${(random() * 2.6 + 2.2).toFixed(2)}s`);
    if (index % 7 === 0) star.classList.add('is-large');
    field.append(star);
  }
  return field;
}

function makeCloudLayer() {
  const random = mulberry32((sceneSeed = (sceneSeed + 1) | 0) * 5501 + 29);
  const layer = makeElement('span', 'card-clouds');
  layer.setAttribute('aria-hidden', 'true');
  for (let index = 0; index < SCENE_CLOUDS; index += 1) {
    const cloud = makeElement('i', 'card-cloud');
    cloud.style.setProperty('--y', `${(random() * 52 + 4).toFixed(2)}%`);
    cloud.style.setProperty('--scale', `${(random() * 0.5 + 0.6).toFixed(2)}`);
    cloud.style.setProperty('--drift', `${(random() * 12 + 20).toFixed(1)}s`);
    cloud.style.setProperty('--delay', `${(-random() * 26).toFixed(1)}s`);
    layer.append(cloud);
  }
  return layer;
}

function makeCardScene(theme) {
  if (theme !== 'night' && theme !== 'day') return null;
  const scene = makeElement('span', `card-scene card-scene-${theme}`);
  scene.setAttribute('aria-hidden', 'true');
  scene.append(theme === 'night' ? makeStarField() : makeCloudLayer());
  if (theme === 'night') {
    const moon = makeElement('i', 'card-moon');
    const shooting = makeElement('i', 'card-shooting-star');
    scene.append(moon, shooting);
  } else {
    const sun = makeElement('i', 'card-sun');
    scene.append(sun);
  }
  return scene;
}


function assetUrl(path) {
  return new URL(`./${encodePath(path)}`, document.baseURI).href;
}

function plural(number, singular, pluralForm = `${singular}s`) {
  return `${number.toLocaleString('en')} ${number === 1 ? singular : pluralForm}`;
}

function makeElement(tag, className, text) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text !== undefined) element.textContent = text;
  return element;
}

function makeCard(collection) {
  const theme = collection.theme || 'plain';
  const card = makeElement('article', `collection-card card-theme-${theme}`);
  card.dataset.theme = theme;
  card.dataset.search = `${collection.name} ${formatFolderDate(collection.date)}`.toLocaleLowerCase();

  const heading = makeElement('div', 'card-heading');
  const folderIcon = makeElement('span', 'folder-icon');
  folderIcon.dataset.theme = theme;
  folderIcon.innerHTML = icons.folder;
  const headingText = makeElement('div', 'card-heading-text');
  const title = makeElement('h2', 'card-title', collection.name);
  title.title = collection.name;
  const details = [plural(collection.images.length, 'image'), formatFileSize(collection.bytes)].filter(Boolean);
  const subtitle = makeElement('p', 'card-subtitle', details.join(' · '));
  headingText.append(title, subtitle);
  const githubLink = makeElement('a', 'icon-button github-link');
  githubLink.href = `https://github.com/${REPOSITORY}/tree/${encodeURIComponent(BRANCH)}/${encodePath(collection.name)}`;
  githubLink.target = '_blank';
  githubLink.rel = 'noopener noreferrer';
  githubLink.setAttribute('aria-label', `Open ${collection.name} on GitHub (opens in a new tab)`);
  githubLink.title = 'Open folder on GitHub';
  githubLink.innerHTML = icons.external;
  heading.append(folderIcon, headingText, githubLink);

  // The preview area is a button too, so it works like opening a Drive folder.
  const previewArea = makeElement('button', 'card-preview');
  previewArea.type = 'button';
  previewArea.setAttribute('aria-label', `Preview ${collection.name}`);
  previewArea.addEventListener('click', () => openPreview(collection));
  const scene = makeCardScene(theme);
  if (scene) previewArea.append(scene);

  const previews = choosePreviews(collection, 4);
  if (previews.length) {
    const grid = makeElement('div', 'card-image-grid');
    grid.dataset.count = String(previews.length);
    previews.forEach(file => {
      const frame = makeElement('span', 'card-image');
      const image = document.createElement('img');
      image.src = assetUrl(file.path);
      image.alt = '';
      image.loading = 'lazy';
      image.decoding = 'async';
      frame.append(image);
      grid.append(frame);
    });
    previewArea.append(grid);
  } else {
    const placeholder = makeElement('span', 'preview-placeholder');
    placeholder.innerHTML = icons.folder;
    placeholder.append(document.createTextNode('No image preview'));
    previewArea.append(placeholder);
  }

  const bottom = makeElement('div', 'card-bottom');
  const meta = makeElement('div', 'card-meta');
  meta.append(
    makeElement('span', '', formatFolderDate(collection.date)),
    makeElement('span', '', plural(collection.files.length, 'file')),
  );
  const actions = makeElement('div', 'card-actions');
  const preview = makeElement('button', 'button button-secondary');
  preview.type = 'button';
  preview.innerHTML = `${icons.eye}<span>Preview</span>`;
  preview.append(makeElement('span', 'visually-hidden', ` ${collection.name}`));
  preview.addEventListener('click', () => openPreview(collection));
  const download = makeElement('button', 'button button-primary download-button');
  download.type = 'button';
  // Only fixed icons use innerHTML; folder and file names always use textContent.
  download.innerHTML = `${icons.download}<span class="button-label">Download</span>`;
  download.append(makeElement('span', 'visually-hidden', ` folder ${collection.name} as a ZIP`));
  const status = makeElement('p', 'download-status');
  status.setAttribute('role', 'status');
  download.addEventListener('click', () => downloadCollection(collection, download, status));
  actions.append(preview, download);
  bottom.append(meta, actions, status);
  card.append(heading, previewArea, bottom);
  return card;
}

function openPreview(collection) {
  elements.previewTitle.textContent = collection.name;
  elements.previewSummary.textContent = [
    plural(collection.images.length, 'image'),
    plural(collection.files.length, 'file'),
    formatFileSize(collection.bytes),
  ].filter(Boolean).join(' · ');
  elements.previewContent.replaceChildren();

  if (collection.previews.length) {
    const gallery = makeElement('div', 'preview-gallery');
    collection.previews.forEach(file => {
      const link = makeElement('a', 'preview-file');
      link.href = assetUrl(file.path);
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      link.setAttribute('aria-label', `Open ${file.relativePath} (opens in a new tab)`);
      const image = document.createElement('img');
      image.src = assetUrl(file.path);
      image.alt = '';
      image.loading = 'lazy';
      image.decoding = 'async';
      const name = makeElement('span', 'preview-filename', file.relativePath);
      name.title = file.relativePath;
      link.append(image, name);
      gallery.append(link);
    });
    elements.previewContent.append(gallery);
  }

  const otherFiles = collection.files.filter(file => !file.isPreviewable);
  if (otherFiles.length) {
    const section = makeElement('section', 'other-files');
    section.append(makeElement('h3', '', 'OTHER FILES'));
    const list = makeElement('div', 'other-files-list');
    otherFiles.forEach(file => {
      const link = makeElement('a', 'other-file');
      link.href = assetUrl(file.path);
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      link.innerHTML = icons.file;
      link.append(makeElement('span', '', file.relativePath));
      list.append(link);
    });
    section.append(list);
    elements.previewContent.append(section);
  }

  document.body.classList.add('dialog-open');
  if (typeof elements.dialog.showModal === 'function') elements.dialog.showModal();
  else elements.dialog.setAttribute('open', '');
  elements.closePreview.focus({ preventScroll: true });
}

async function downloadCollection(collection, button, status) {
  const label = button.querySelector('.button-label');
  button.disabled = true;
  status.classList.remove('is-error');
  status.textContent = 'Getting your files ready. Keep this page open.';
  label.textContent = 'Preparing…';
  try {
    const blob = await createFolderZip(collection, {
      JSZip: window.JSZip,
      fetchFile: (path, options) => fetch(assetUrl(path), options),
      onProgress(progress) {
        if (progress.phase === 'fetch') {
          label.textContent = 'Preparing…';
          status.textContent = `Gathering files ${progress.completed} of ${progress.total}…`;
        } else {
          label.textContent = 'Packing…';
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
    console.error('Folder download failed:', error);
    status.classList.add('is-error');
    status.textContent = 'Couldn’t prepare the ZIP. Try again or open the folder on GitHub.';
  } finally {
    button.disabled = false;
    label.textContent = 'Download';
  }
}

function applySearch() {
  const query = elements.search.value.trim().toLocaleLowerCase();
  let visible = 0;
  for (const { card } of cards) {
    const show = card.dataset.search.includes(query);
    card.hidden = !show;
    if (show) visible += 1;
  }
  elements.empty.hidden = visible !== 0 || cards.length === 0;
  elements.count.textContent = query ? 'SEARCH RESULTS' : 'ALL FOLDERS';
  elements.visibleCount.textContent = `/ ${String(visible).padStart(2, '0')}`;
}

function renderCollections(collections) {
  elements.folderTotal.textContent = String(collections.length).padStart(2, '0');
  elements.folderTotal.setAttribute('aria-label', plural(collections.length, 'folder'));
  elements.list.replaceChildren();
  cards = collections.map(collection => ({ card: makeCard(collection) }));
  cards.forEach(({ card }) => elements.list.append(card));
  elements.list.setAttribute('aria-busy', 'false');
  if (!collections.length) {
    const message = makeElement('div', 'loading-state', 'No folders yet.');
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
      showSyncMessage('Showing a saved snapshot. Newer folders may be missing until the connection is restored.');
    } catch (snapshotError) {
      console.error('Saved catalog unavailable:', snapshotError);
      cards = [];
      elements.list.replaceChildren();
      elements.list.setAttribute('aria-busy', 'false');
      elements.empty.hidden = true;
      elements.folderTotal.textContent = '?';
      elements.folderTotal.setAttribute('aria-label', 'Number of folders unavailable');
      elements.visibleCount.textContent = '';
      showSyncMessage('Couldn’t load folders.');
    }
  } finally {
    clearTimeout(timeout);
  }
}

elements.search.addEventListener('input', applySearch);
document.addEventListener('keydown', event => {
  if (elements.dialog.open || event.key !== '/' || event.ctrlKey || event.metaKey || event.altKey) return;
  const target = event.target;
  if (target instanceof HTMLElement && (target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName))) return;
  event.preventDefault();
  elements.search.focus();
});
function closePreview() {
  document.body.classList.remove('dialog-open');
  if (typeof elements.dialog.close === 'function') elements.dialog.close();
  else {
    elements.dialog.removeAttribute('open');
    elements.previewContent.replaceChildren();
  }
}
elements.closePreview.addEventListener('click', closePreview);
elements.dialog.addEventListener('click', event => {
  if (event.target === elements.dialog) closePreview();
});
elements.dialog.addEventListener('cancel', () => document.body.classList.remove('dialog-open'));
elements.dialog.addEventListener('close', () => {
  if (elements.dialog.open) return;
  document.body.classList.remove('dialog-open');
  elements.previewContent.replaceChildren();
});

loadCollections();
