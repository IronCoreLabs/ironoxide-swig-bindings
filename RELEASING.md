# Release process

1. Create and push a new branch starting with `release-v`, like `release-v0.13.0`.
1. The `release1.yaml` workflow will set the version (e.g., `0.13.0`) in various files and create a new PR for the release.
1. CI will run on the PR. Then it can either be approved and merged, or closed.
1. Merging the PR will cause `release2.yaml` to run, which builds and publishes the release.
1. After publishing the release, it increments Java-related versions to the next `-SNAPSHOT` release.
