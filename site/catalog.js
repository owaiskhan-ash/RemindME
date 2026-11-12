export const REPOSITORY = 'owaiskhan-ash/Storage-Books';
export const BRANCH = 'main';

// The original folders were created in 2026 and do not include a year.
// New folders should include the year so they remain unambiguous over time.
export const LEGACY_YEAR = 2026;

const RESERVED_DIRECTORIES = new Set(['site', 'scripts', 'tests', 'test', 'node_modules', 'dist', 'build']);
const MONTHS = {
  jan: 1, january: 1, feb: 2, february: 2, mar: 3, march: 3,
  apr: 4, april: 4, may: 5, jun: 6, june: 6, jul: 7, july: 7,
  aug: 8, august: 8, sep: 9, sept: 9, september: 9, oct: 10,
  october: 10, nov: 11, november: 11, dec: 12, december: 12,
};
const MONTH_PATTERN = '(?:january|jan|february|feb|march|mar|april|apr|may|june|jun|july|jul|august|aug|september|sept|sep|october|oct|november|nov|december|dec)';
const dayFirst = new RegExp(`\\b(\\d{1,2})(?:st|nd|rd|th)?[\\s._-]+(${MONTH_PATTERN})\\b`, 'i');
const monthFirst = new RegExp(`\\b(${MONTH_PATTERN})[\\s._-]+(\\d{1,2})(?:st|nd|rd|th)?\\b`, 'i');
const collator = new Intl.Collator('en', { numeric: true, sensitivity: 'base' });
const IMAGE_EXTENSION = /\.(?:avif|bmp|gif|heic|heif|jpe?g|png|svg|tiff?|webp)$/i;
const PREVIEW_EXTENSION = /\.(?:avif|gif|jpe?g|png|svg|webp)$/i;

export function isReservedDirectory(name) {
  return name.startsWith('.') || RESERVED_DIRECTORIES.has(name.toLowerCase());
}

function validDate(year, month, day, inferredYear) {
  const timestamp = Date.UTC(year, month - 1, day);
  const date = new Date(timestamp);
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) return null;
  return { year, month, day, inferredYear, timestamp };
}

/** Parse common dated folder names without relying on browser locale. */
export function parseFolderDate(name) {
  const iso = name.match(/(?:^|[^\d])((?:19|20)\d{2})[-_./](\d{1,2})[-_./](\d{1,2})(?!\d)/);
  if (iso) return validDate(Number(iso[1]), Number(iso[2]), Number(iso[3]), false);

  const numeric = name.match(/(?:^|[^\d])(\d{1,2})[-_./](\d{1,2})[-_./]((?:19|20)\d{2})(?!\d)/);
  if (numeric) return validDate(Number(numeric[3]), Number(numeric[2]), Number(numeric[1]), false);

  const firstDay = name.match(dayFirst);
  const firstMonth = name.match(monthFirst);
  if (!firstDay && !firstMonth) return null;

  const yearMatch = name.match(/\b(?:19|20)\d{2}\b/);
  const year = yearMatch ? Number(yearMatch[0]) : LEGACY_YEAR;
  const month = MONTHS[(firstDay ? firstDay[2] : firstMonth[1]).toLowerCase()];
  const day = Number(firstDay ? firstDay[1] : firstMonth[2]);
  return validDate(year, month, day, !yearMatch);
}

export function isImage(path) { return IMAGE_EXTENSION.test(path); }
export function isPreviewableImage(path) { return PREVIEW_EXTENSION.test(path); }

/** Turn a Git tree (or local snapshot in the same format) into collections. */
export function collectionsFromTree(tree) {
  const groups = new Map();
  for (const item of tree) {
    if (item.type !== 'blob' || typeof item.path !== 'string') continue;
    const slash = item.path.indexOf('/');
    if (slash < 1) continue;
    const name = item.path.slice(0, slash);
    const relativePath = item.path.slice(slash + 1);
    if (isReservedDirectory(name) || !relativePath || relativePath.split('/').some(part => part === '.' || part === '..')) continue;
    if (!groups.has(name)) groups.set(name, []);
    groups.get(name).push({
      path: item.path,
      relativePath,
      size: Number.isFinite(item.size) && item.size >= 0 ? item.size : 0,
      isImage: isImage(relativePath),
      isPreviewable: isPreviewableImage(relativePath),
    });
  }

  return [...groups.entries()].map(([name, files]) => {
    files.sort((a, b) => collator.compare(a.relativePath, b.relativePath));
    const images = files.filter(file => file.isImage);
    return {
      name,
      date: parseFolderDate(name),
      files,
      images,
      previews: files.filter(file => file.isPreviewable),
      bytes: files.reduce((sum, file) => sum + file.size, 0),
    };
  }).sort((a, b) => {
    if (a.date && b.date && a.date.timestamp !== b.date.timestamp) return b.date.timestamp - a.date.timestamp;
    if (a.date && !b.date) return -1;
    if (!a.date && b.date) return 1;
    return collator.compare(a.name, b.name);
  });
}

export function formatFolderDate(date) {
  if (!date) return 'UNDATED FOLDER';
  const month = new Intl.DateTimeFormat('en', { month: 'long', timeZone: 'UTC' }).format(new Date(date.timestamp));
  return `${month.toUpperCase()} ${String(date.day).padStart(2, '0')}${date.inferredYear ? '' : `, ${date.year}`}`;
}

export function formatFileSize(bytes) {
  if (!bytes) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

export function encodePath(path) {
  return path.split('/').map(encodeURIComponent).join('/');
}

/** Prefer lightweight previews; the download still contains the untouched originals. */
export function choosePreviews(collection, count) {
  const light = collection.previews.filter(file => file.size > 0 && file.size <= 750_000);
  const candidates = light.length >= count ? light : collection.previews;
  if (candidates.length <= count) return candidates;
  if (count === 1) return [candidates[0]];
  return Array.from({ length: count }, (_, index) => candidates[Math.round(index * (candidates.length - 1) / (count - 1))]);
}
