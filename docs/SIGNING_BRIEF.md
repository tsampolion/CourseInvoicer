# Signing: the owner's key on `Rebuild_v2` only

Decision D23 in [`PRODUCT_SPEC.md`](PRODUCT_SPEC.md), shipped in 2.1.1. It mirrors Course Pacer 1.7.3.
The key itself does not change.

## Why

Until 2.1.0:

- CI decoded the owner's keystore on **every** branch push and wrote its password to `GITHUB_ENV`, so
  every later step of the job, and any branch's own build files, could read it (audit S5).
- The **debug** build was signed with the owner's key under the real `applicationId`. Any branch's
  `app-debug.apk` installed over the tutor's real app, data included.

## What changed

- **Release environment.** The secrets `SIGNING_KEYSTORE_B64` and `SIGNING_PASSWORD` live in a GitHub
  environment called `release`, whose deployment branches are limited to `Rebuild_v2`. CI's build
  job uses that environment only on `Rebuild_v2`; every other branch gets no secrets.
  `SIGNING_CERT_SHA256` (and the optional `SIGNING_KEY_ALIAS`) stay repository variables: they are
  public.
- **Only the release build is signed with the owner's key.** The *Assemble release* step decodes the
  keystore into `$RUNNER_TEMP`, passes its path and password to Gradle through step-level
  environment variables (`INVOICER_RELEASE_KEYSTORE`, `INVOICER_KEYSTORE_PASSWORD`,
  `INVOICER_KEY_ALIAS`), builds without a daemon and deletes the file when the step ends. Nothing is
  written to `GITHUB_ENV` but the flag `SIGNED_WITH_RELEASE_KEY=true`.
- **Missing secrets.** On `Rebuild_v2` the build fails. On any other branch it prints a notice and
  signs the release APK with the debug key, so it can never be published.
- **Certificate check.** CI prints the certificate of both APKs. On `Rebuild_v2`, or whenever the
  owner's key was used, `app-release.apk` must have exactly one signer whose SHA-256 fingerprint is
  `SIGNING_CERT_SHA256`. `release.yml` runs in the same `release` environment and checks the
  fingerprint again before it publishes.
- **The debug build is its own app.** `applicationIdSuffix = ".debug"` makes it
  `gr.edusuite.invoicer.debug`, labelled *Course Invoicer (debug)* and signed with the stock debug
  key. It installs beside the real app with its own data and can never replace it.
- **Builds without the key** (Android Studio, local `assembleRelease`) sign with the debug key, as
  before.

## Owner steps (a pull request cannot do these)

1. GitHub → *Settings → Environments* → *New environment* `release`. Under *Deployment branches and
   tags*, choose *Selected branches and tags* and add `Rebuild_v2`. No required reviewers.
2. In that environment add the secrets `SIGNING_KEYSTORE_B64` and `SIGNING_PASSWORD`, with the same
   values as today. Then delete the repository-level copies (*Settings → Secrets and variables →
   Actions*), so nothing but `Rebuild_v2` can read them. `SIGNING_CERT_SHA256` stays a repository
   variable.

Nothing breaks in between: environments add to repository secrets, so `Rebuild_v2` stays signed with
the owner's key throughout. But until the repository-level copies are deleted, every branch still
receives them, and its `app-release.apk` is signed with the owner's key too. The protection starts
with step 2. The debug app is separate from the first build after the merge either way.

## Rules from now on

- Never regenerate, replace or "fix" the key, and never change its alias, the secret names or the
  variable without the owner. A new key means every phone uninstalls, which deletes its data.
- Never paste the keystore, its base64 or the password into a chat, an AI session, an issue, a pull
  request or a commit. If one is exposed, treat the key as leaked; rotating it needs APK Signature
  Scheme v3 lineage and is its own task.
- Anyone with write access to the repository can read the secrets through a workflow on
  `Rebuild_v2`, so write access stays with the owner.
