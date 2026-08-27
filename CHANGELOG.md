# Changelog

## 2.2.1

- anchor Android certificate trust to `mozilla/webpki-roots` public CAs. All the certificate stores available to us stopped publishing OCSP responders, which Android's revocation checker treats as revocation. As certs are renewed or those providers shut down their OCSP responders on their side, all existing versions of the library will break, so this is a mandatory upgrade. Custom CAs will no longer work, if that is an issue, reach out to support@ironcorelabs.com.

## 2.2.0

- pulled through `user_disable_self` and `user_update_status` functionality, allowing for enabling/disabling users.

## 2.1.0

- pulled through `document_file_[encrypt|decrypt]` and `document_file_[encrypt|decrypt]_unmanaged` functionality, allowing for constant memory use encrypts and decrypts.

## 2.0.1

- update ironoxide, fixing a bug that made loading from cached public keys unnecessarily hit the service.

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
