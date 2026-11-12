/**
 * Fetch every file (including nested files and notes) and package it under its
 * original folder name. Images are already compressed, so STORE avoids wasting
 * time recompressing them. The original repository files are never modified.
 */
export async function createFolderZip(collection, {
  JSZip,
  fetchFile,
  onProgress = () => {},
  concurrency = 4,
  outputType = 'blob',
} = {}) {
  if (!JSZip || !fetchFile) throw new Error('Download support is unavailable.');
  if (!collection.files.length) throw new Error('This folder has no files to download.');

  const zip = new JSZip();
  const folder = zip.folder(collection.name);
  const controller = new AbortController();
  let next = 0;
  let completed = 0;
  const workerCount = Math.max(1, Math.min(Math.floor(concurrency) || 1, collection.files.length));

  async function worker() {
    while (!controller.signal.aborted && next < collection.files.length) {
      const file = collection.files[next++];
      const response = await fetchFile(file.path, { signal: controller.signal });
      if (!response.ok) throw new Error(`Could not fetch ${file.relativePath} (HTTP ${response.status}).`);
      const bytes = await response.arrayBuffer();
      if (controller.signal.aborted) return;
      folder.file(file.relativePath, bytes, { binary: true });
      completed += 1;
      onProgress({ phase: 'fetch', completed, total: collection.files.length });
    }
  }

  try {
    await Promise.all(Array.from({ length: workerCount }, worker));
  } catch (error) {
    controller.abort();
    throw error;
  }

  onProgress({ phase: 'zip', percent: 0 });
  return zip.generateAsync({ type: outputType, compression: 'STORE', streamFiles: true }, metadata => {
    onProgress({ phase: 'zip', percent: Math.round(metadata.percent) });
  });
}
