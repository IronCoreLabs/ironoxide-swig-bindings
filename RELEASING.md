# Release process

1. Create and push a new branch starting with `release-v`, like `release-v0.13.0`.
1. The `start-release.yaml` workflow will set the version (e.g., `0.13.0`) in various files and create a new PR for the release.
1. CI will run on the PR. Then it can either be approved and merged, or closed.
1. Merging the PR will cause the `release-*.yaml` workflows to run, which build and publish the release.
1. After publishing the release, it increments Java-related versions to the next `-SNAPSHOT` release.

# Re-Release

If you need to re-release something, look at the last step of `release1.yaml` or the `if:` statements in `release2.yaml`. Manually
create a PR that matches what `release2.yaml` expects. Then merge the PR, and it'll take over.
