# Release process

1. Create and push a new branch starting with `release-v`, like `release-v0.13.0`.
1. The `start-release.yaml` workflow will set the version (e.g., `0.13.0`) in various files and create a new PR for the release.
1. CI will run on the PR. Then it can either be approved and merged, or closed.
1. Merging the PR will cause the `release-*.yaml` workflows to run, which build and publish the release.
1. After publishing the release, it increments Java-related versions to the next `-SNAPSHOT` release.

# Re-Release

If you need to re-release something, try restarting just the failed release workflow. Each workflow is independent of the others,
and each one has a non-idempotent step near the end. So if one fails due to external dependencies like network problems, there
should be no need to rerun the others.
