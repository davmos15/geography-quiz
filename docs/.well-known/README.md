# Digital Asset Links

`assetlinks.json` lets Android verify that `https://geoquiz-app.netlify.app/challenge…` links
belong to the app (App Links, `android:autoVerify="true"` in `AndroidManifest.xml`). Netlify
publishes `docs/`, so the file is served at
`https://geoquiz-app.netlify.app/.well-known/assetlinks.json`.

Fingerprints (SHA-256 of the signing certificate):

| Package | Certificate |
|---|---|
| `com.geoquiz.app` | Upload key (sideloaded release builds) |
| `com.geoquiz.app.debug` | Local debug keystore (`~/.android/debug.keystore`) |

**Owner action:** add the Play App Signing key's SHA-256 (Play Console → Test and release →
App integrity → App signing key certificate) to the `com.geoquiz.app` list. Apps installed from
Google Play are signed with that key, so App Links won't verify for Play installs until it is
listed. Other debug keystores need their own fingerprint added to the debug entry.

The file must be served over HTTPS with `Content-Type: application/json`, with no redirect.
Check with Google's tester:
`https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://geoquiz-app.netlify.app&relation=delegate_permission/common.handle_all_urls`
