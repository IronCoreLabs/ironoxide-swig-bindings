# Release process

1. Create and push a tag starting with `release-v`, like `release-v0.13.0`.

That's it. It'll cause `.github/workflows/release1.yaml` to run, which will set the version in various files and then create an
empty release. That causes `.github/workflows/release2.yaml` to run, which builds the release files and uploads them to the GitHub
release and other places like Maven Central.
