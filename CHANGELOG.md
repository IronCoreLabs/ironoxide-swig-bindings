# Changelog

## 2.0.0

### Breaking

- renamed `advancedDocumentEncryptUnmanaged -> documentEncryptUnmanaged` and `advanceDocumentDecryptUnmanaged -> documentDecryptUnmanaged` to match the base library and other unmanaged functions.

### Additions

- pulled all the `*_unmanaged` functionality from `DocumentAdvancedOps` on the base library through
- pulled through automatic public key caching, along with initialization and export, supporting offline unmanaged encryption
- dependency updates

## 1.0.2

- Fix issue with ironoxide-android unable to resolve rustls dependency

## 1.0.0

- Dependency updates
  - Notably, ironoxide now reuses request clients when made by a single IronOxide.

## 0.16.0

- [[#221](https://github.com/IronCoreLabs/ironoxide-swig-bindings/pull/221)] Dependency updates
  - Notably, ironoxide now contains policy caching for unmanaged encryption.

_Due to an issue with GitHub Actions, ironoxide-cpp is not included in this release._
