# The Image Archive

A small, static landing page for the image folders in this repository. It lists collections from newest to oldest, lets visitors search, and downloads each folder—including nested files and notes—as a ZIP. Original files are not changed.

## Add a collection

Add a top-level folder to the `main` branch. The page discovers folders automatically from the public repository's GitHub tree; no page code needs to be edited. The `site`, `scripts`, `tests`, `test`, `node_modules`, `dist`, `build`, and hidden directories are reserved for site infrastructure and are not displayed as collections. Folders without a recognizable date appear after dated folders.

For reliable ordering across years, include a four-digit year in new folder names. Examples: `9th Nov 2026`, `November 9, 2026`, `09-11-2026` (day-month-year), or `2026-11-09`. Older names without a year, such as `7th Nov Day`, are treated as **2026** for sorting. The year is not displayed when it is not in the name.

The page fetches its catalog from GitHub. If that request is unavailable or rate-limited, it shows `site/snapshot.json` instead and clearly labels it as a potentially older snapshot. To refresh that fallback after adding folders, run `npm run snapshot` and commit the updated snapshot. The live catalog does not require the snapshot to be updated.

## Run locally

With Node.js and Python 3 installed:

```sh
npm start
```

Open `http://localhost:4173`. Run the tests with `npm test`. No install or build step is required. The page uses the public `main` branch for the live catalog, so uncommitted local folders will not appear unless the live request is unavailable and the fallback snapshot is regenerated.

The site can be hosted directly from the repository root. To publish with GitHub Pages, open **Settings → Pages**, choose **Deploy from a branch**, then select `main` and `/(root)` and save. The expected address is `https://owaiskhan-ash.github.io/RemindME/` after the initial deployment completes.

The site uses relative file URLs, so the image folders must be published alongside `index.html`. If this repository is forked or renamed, update `REPOSITORY` and `BRANCH` in `site/catalog.js` and the fallback GitHub link in `index.html`.

JSZip and the self-hosted fonts are included in `site/`; their licenses are alongside them.
