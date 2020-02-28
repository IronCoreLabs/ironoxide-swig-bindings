mod jni_c_header;
use ironoxide::{
    blocking::BlockingIronOxide as IronOxide,
    config::{IronOxideConfig, PolicyCachingConfig},
    document::{
        advanced::{DocumentDecryptUnmanagedResult, DocumentEncryptUnmanagedResult},
        AssociationType, DocAccessEditErr, DocumentAccessResult, DocumentDecryptResult,
        DocumentEncryptOpts, DocumentEncryptResult, DocumentListMeta, DocumentListResult,
        DocumentMetadataResult, UserOrGroup, VisibleGroup, VisibleUser,
    },
    group::{
        GroupAccessEditErr, GroupAccessEditResult, GroupCreateOpts, GroupCreateResult,
        GroupGetResult, GroupListResult, GroupMetaResult, GroupUpdatePrivateKeyResult,
    },
    policy::{Category, DataSubject, PolicyGrant, Sensitivity},
    prelude::*,
    user::{
        DeviceCreateOpts, EncryptedPrivateKey, UserCreateOpts, UserCreateResult, UserDevice,
        UserDeviceListResult, UserResult, UserUpdatePrivateKeyResult,
    },
    DeviceAddResult, DeviceContext, DeviceSigningKeyPair, InitAndRotationCheck, PrivateKey,
    PublicKey,
};
use std::{
    collections::hash_map::DefaultHasher,
    convert::TryInto,
    hash::{Hash, Hasher},
    time::Duration,
};

include!(concat!(env!("OUT_DIR"), "/lib.rs"));

pub fn hash<T: Hash>(t: &T) -> i32 {
    let mut s = DefaultHasher::new();
    t.hash(&mut s);
    s.finish() as i32
}

pub fn eq<T: PartialEq>(t: &T, other: &T) -> bool {
    t.eq(other)
}

#[derive(Clone, Debug, Hash, PartialEq, Eq)]
pub struct UserWithKey((UserId, PublicKey));
impl UserWithKey {
    pub fn user(&self) -> UserId {
        (self.0).0.clone()
    }

    pub fn public_key(&self) -> PublicKey {
        (self.0).1.clone()
    }
}

// This was created because Option<bool> cannot be converted to Java
#[derive(Clone, Debug, Hash, PartialEq, Eq)]
pub struct NullableBoolean(bool);
impl NullableBoolean {
    pub fn boolean(&self) -> bool {
        self.0
    }
}

fn i8_conv(i8s: &[i8]) -> &[u8] {
    unsafe { core::slice::from_raw_parts(i8s.as_ptr() as *const u8, i8s.len()) }
}

fn u8_conv(u8s: &[u8]) -> &[i8] {
    unsafe { core::slice::from_raw_parts(u8s.as_ptr() as *const i8, u8s.len()) }
}

mod visible_user {
    use super::*;
    pub fn id(d: &VisibleUser) -> UserId {
        d.id().clone()
    }
}

mod visible_group {
    use super::*;
    pub fn id(d: &VisibleGroup) -> GroupId {
        d.id().clone()
    }
    pub fn name(d: &VisibleGroup) -> Option<GroupName> {
        d.name().cloned()
    }
}

mod user_id {
    use super::*;
    pub fn id(u: &UserId) -> String {
        u.id().to_string()
    }

    pub fn validate(s: &str) -> Result<UserId, String> {
        Ok(s.try_into()?)
    }
}

mod group_id {
    use super::*;
    use std::convert::TryInto;
    pub fn id(g: &GroupId) -> String {
        g.id().to_string()
    }

    pub fn validate(s: &str) -> Result<GroupId, String> {
        Ok(s.try_into()?)
    }
}

mod group_name {
    use super::*;
    use std::convert::TryInto;
    pub fn name(g: &GroupName) -> String {
        g.name().clone()
    }
    pub fn validate(g: &str) -> Result<GroupName, String> {
        Ok(g.try_into()?)
    }
}

mod document_id {
    use super::*;
    use std::convert::TryInto;
    pub fn id(d: &DocumentId) -> String {
        d.id().to_string()
    }
    pub fn validate(s: &str) -> Result<DocumentId, String> {
        Ok(s.try_into()?)
    }
}

mod document_name {
    use super::*;
    use std::convert::TryInto;
    pub fn name(d: &DocumentName) -> String {
        d.name().clone()
    }
    pub fn validate(d: &str) -> Result<DocumentName, String> {
        Ok(d.try_into()?)
    }
}

mod device_id {
    use super::*;
    use std::convert::TryInto;
    pub fn id(d: &DeviceId) -> i64 {
        //By construction, DeviceIds are validated to be at most i64 max so this value won't
        //wrap over to be negative
        *d.id() as i64
    }
    pub fn validate(s: i64) -> Result<DeviceId, String> {
        Ok((s as u64).try_into()?)
    }
}

mod device_name {
    use super::*;
    use std::convert::TryInto;
    pub fn name(d: &DeviceName) -> String {
        d.name().clone()
    }
    pub fn validate(n: &str) -> Result<DeviceName, String> {
        Ok(n.try_into()?)
    }
}

mod public_key {
    use super::*;
    use std::convert::TryInto;
    pub fn validate(bytes: &[i8]) -> Result<PublicKey, String> {
        Ok(i8_conv(bytes).try_into()?)
    }
    pub fn as_bytes(pk: &PublicKey) -> Vec<i8> {
        u8_conv(&pk.as_bytes()[..]).to_vec()
    }
}

mod private_key {
    use super::*;
    use std::convert::TryInto;
    pub fn validate(bytes: &[i8]) -> Result<PrivateKey, String> {
        Ok(i8_conv(bytes).try_into()?)
    }
    pub fn as_bytes(pk: &PrivateKey) -> Vec<i8> {
        u8_conv(&pk.as_bytes()[..]).to_vec()
    }
}

mod device_signing_keys {
    use super::*;
    use std::convert::TryInto;
    pub fn validate(bytes: &[i8]) -> Result<DeviceSigningKeyPair, String> {
        Ok(i8_conv(bytes).try_into()?)
    }
    pub fn as_bytes(pk: &DeviceSigningKeyPair) -> Vec<i8> {
        u8_conv(&pk.as_bytes()[..]).to_vec()
    }
}

mod device_create_opts {
    use super::*;
    pub fn create(name: Option<&DeviceName>) -> DeviceCreateOpts {
        DeviceCreateOpts::new(name.cloned())
    }
}

mod user_create_opts {
    use super::*;
    pub fn create(needs_rotation: bool) -> UserCreateOpts {
        UserCreateOpts::new(needs_rotation)
    }
}

mod policy_grant {
    use super::*;
    pub fn create(
        category: Option<&Category>,
        sensitivity: Option<&Sensitivity>,
        data_subject: Option<&DataSubject>,
        sub_id: Option<&UserId>,
    ) -> PolicyGrant {
        PolicyGrant::new(
            category.cloned(),
            sensitivity.cloned(),
            data_subject.cloned(),
            sub_id.cloned(),
        )
    }
    pub fn category(p: &PolicyGrant) -> Option<Category> {
        p.category().cloned()
    }
    pub fn sensitivity(p: &PolicyGrant) -> Option<Sensitivity> {
        p.sensitivity().cloned()
    }
    pub fn data_subject(p: &PolicyGrant) -> Option<DataSubject> {
        p.data_subject().cloned()
    }
    pub fn substitute_id(p: &PolicyGrant) -> Option<UserId> {
        p.substitute_user().cloned()
    }
}

mod category {
    use super::*;

    pub fn validate(s: &str) -> Result<Category, String> {
        Ok(s.try_into()?)
    }
    pub fn value(c: &Category) -> String {
        c.inner().to_string()
    }
}

mod sensitivity {
    use super::*;

    pub fn validate(s: &str) -> Result<Sensitivity, String> {
        Ok(s.try_into()?)
    }
    pub fn value(s: &Sensitivity) -> String {
        s.inner().to_string()
    }
}

mod data_subject {
    use super::*;

    pub fn validate(s: &str) -> Result<DataSubject, String> {
        Ok(s.try_into()?)
    }
    pub fn value(d: &DataSubject) -> String {
        d.inner().to_string()
    }
}

mod document_create_opt {
    use super::*;
    use ironoxide::document::{DocumentEncryptOpts, ExplicitGrant};
    use itertools::EitherOrBoth;
    pub fn create(
        id: Option<&DocumentId>,
        name: Option<&DocumentName>,
        grant_to_author: bool,
        user_grants: Vec<UserId>,
        group_grants: Vec<GroupId>,
        policy_grant: Option<&PolicyGrant>,
    ) -> DocumentEncryptOpts {
        let users_and_groups: Vec<UserOrGroup> = user_grants
            .into_iter()
            .map(|u| UserOrGroup::User { id: u })
            .chain(
                group_grants
                    .into_iter()
                    .map(|g| UserOrGroup::Group { id: g }),
            )
            .collect();

        let explicit = ExplicitGrant::new(grant_to_author, &users_and_groups);
        let grants = match policy_grant.cloned() {
            Some(policy) => EitherOrBoth::Both(explicit, policy),
            None => EitherOrBoth::Left(explicit),
        };
        DocumentEncryptOpts::new(id.cloned(), name.cloned(), grants)
    }
}

mod device_context {
    use super::*;
    pub fn new(
        account_id: &UserId,
        segment_id: i64,
        device_private_key: &PrivateKey,
        signing_private_key: &DeviceSigningKeyPair,
    ) -> DeviceContext {
        DeviceContext::new(
            account_id.clone(),
            segment_id as usize,
            device_private_key.clone(),
            signing_private_key.clone(),
        )
    }
    pub fn new_from_dar(dar: &DeviceAddResult) -> DeviceContext {
        dar.clone().into()
    }
    pub fn account_id(d: &DeviceContext) -> UserId {
        d.account_id().clone()
    }

    pub fn segment_id(d: &DeviceContext) -> usize {
        d.segment_id()
    }

    pub fn device_private_key(d: &DeviceContext) -> PrivateKey {
        d.device_private_key().clone()
    }

    pub fn signing_private_key(d: &DeviceContext) -> DeviceSigningKeyPair {
        d.signing_private_key().clone()
    }

    pub fn to_json_string(d: &DeviceContext) -> String {
        serde_json::to_string(d).expect("DeviceContext should always serialize to JSON")
    }

    pub fn from_json_string(json_string: &str) -> Result<DeviceContext, String> {
        serde_json::from_str(json_string).map_err(|_| {
            "jsonString was not a valid JSON representation of a DeviceContext.".to_string()
        })
    }
}

mod device_add_result {
    use super::*;
    pub fn account_id(d: &DeviceAddResult) -> UserId {
        d.account_id().clone()
    }
    pub fn segment_id(d: &DeviceAddResult) -> usize {
        d.segment_id()
    }
    pub fn device_private_key(d: &DeviceAddResult) -> PrivateKey {
        d.device_private_key().clone()
    }
    pub fn signing_private_key(d: &DeviceAddResult) -> DeviceSigningKeyPair {
        d.signing_private_key().clone()
    }
    pub fn device_id(d: &DeviceAddResult) -> DeviceId {
        d.device_id().clone()
    }
    pub fn name(d: &DeviceAddResult) -> Option<DeviceName> {
        d.name().cloned()
    }
    pub fn created(d: &DeviceAddResult) -> DateTime<Utc> {
        *d.created()
    }
    pub fn last_updated(d: &DeviceAddResult) -> DateTime<Utc> {
        *d.last_updated()
    }
}

mod user_create_result {
    use super::*;
    pub fn user_public_key(u: &UserCreateResult) -> PublicKey {
        u.user_public_key().clone()
    }
    pub fn needs_rotation(u: &UserCreateResult) -> bool {
        u.needs_rotation()
    }
}

mod user_result {
    use super::*;
    pub fn user_public_key(u: &UserResult) -> PublicKey {
        u.user_public_key().clone()
    }

    pub fn account_id(u: &UserResult) -> UserId {
        u.account_id().clone()
    }

    pub fn segment_id(u: &UserResult) -> usize {
        u.segment_id()
    }

    pub fn needs_rotation(u: &UserResult) -> bool {
        u.needs_rotation()
    }
}

mod user_update_private_key_result {
    use super::*;
    pub fn user_master_private_key(u: &UserUpdatePrivateKeyResult) -> EncryptedPrivateKey {
        u.user_master_private_key().clone()
    }
    pub fn needs_rotation(u: &UserUpdatePrivateKeyResult) -> bool {
        u.needs_rotation()
    }
}

mod user_device {
    use super::*;
    pub fn id(u: &UserDevice) -> DeviceId {
        u.id().clone()
    }

    pub fn name(u: &UserDevice) -> Option<DeviceName> {
        u.name().cloned()
    }

    pub fn created(u: &UserDevice) -> DateTime<Utc> {
        *u.created()
    }

    pub fn last_updated(u: &UserDevice) -> DateTime<Utc> {
        *u.last_updated()
    }
}

mod user_device_list_result {
    use super::*;
    pub fn result(u: &UserDeviceListResult) -> Vec<UserDevice> {
        u.result().clone()
    }
}

mod document_list_result {
    use super::*;
    pub fn result(d: &DocumentListResult) -> Vec<DocumentListMeta> {
        d.result().to_vec()
    }
}

mod document_list_meta {
    use super::*;
    pub fn id(d: &DocumentListMeta) -> DocumentId {
        d.id().clone()
    }
    pub fn name(d: &DocumentListMeta) -> Option<DocumentName> {
        d.name().cloned()
    }
    pub fn association_type(d: &DocumentListMeta) -> AssociationType {
        d.association_type().clone()
    }
    pub fn created(d: &DocumentListMeta) -> DateTime<Utc> {
        *d.created()
    }
    pub fn last_updated(d: &DocumentListMeta) -> DateTime<Utc> {
        *d.last_updated()
    }
}

mod document_metadata_result {
    use super::*;
    pub fn id(d: &DocumentMetadataResult) -> DocumentId {
        d.id().clone()
    }
    pub fn name(d: &DocumentMetadataResult) -> Option<DocumentName> {
        d.name().cloned()
    }
    pub fn created(d: &DocumentMetadataResult) -> DateTime<Utc> {
        *d.created()
    }
    pub fn last_updated(d: &DocumentMetadataResult) -> DateTime<Utc> {
        *d.last_updated()
    }
    pub fn association_type(d: &DocumentMetadataResult) -> AssociationType {
        d.association_type().clone()
    }
    pub fn visible_to_users(d: &DocumentMetadataResult) -> Vec<VisibleUser> {
        d.visible_to_users().clone()
    }
    pub fn visible_to_groups(d: &DocumentMetadataResult) -> Vec<VisibleGroup> {
        d.visible_to_groups().clone()
    }
}

mod document_encrypt_result {
    use super::*;

    pub fn id(d: &DocumentEncryptResult) -> DocumentId {
        d.id().clone()
    }
    pub fn name(d: &DocumentEncryptResult) -> Option<DocumentName> {
        d.name().cloned()
    }
    pub fn created(d: &DocumentEncryptResult) -> DateTime<Utc> {
        *d.created()
    }
    pub fn last_updated(d: &DocumentEncryptResult) -> DateTime<Utc> {
        *d.last_updated()
    }
    pub fn encrypted_data(d: &DocumentEncryptResult) -> Vec<i8> {
        u8_conv(d.encrypted_data()).to_vec()
    }
}

mod encrypted_private_key {
    use super::*;
    pub fn as_bytes(e: &EncryptedPrivateKey) -> Vec<i8> {
        u8_conv(e.as_bytes()).to_vec()
    }
}

mod document_encrypt_unmanaged_result {
    use super::*;

    pub fn id(d: &DocumentEncryptUnmanagedResult) -> DocumentId {
        d.id().clone()
    }
    pub fn encrypted_data(d: &DocumentEncryptUnmanagedResult) -> Vec<i8> {
        u8_conv(d.encrypted_data()).to_vec()
    }
    pub fn encrypted_deks(d: &DocumentEncryptUnmanagedResult) -> Vec<i8> {
        u8_conv(d.encrypted_deks()).to_vec()
    }
}

mod document_decrypt_result {
    use super::*;

    pub fn id(d: &DocumentDecryptResult) -> DocumentId {
        d.id().clone()
    }
    pub fn name(d: &DocumentDecryptResult) -> Option<DocumentName> {
        d.name().cloned()
    }
    pub fn created(d: &DocumentDecryptResult) -> DateTime<Utc> {
        *d.created()
    }
    pub fn last_updated(d: &DocumentDecryptResult) -> DateTime<Utc> {
        *d.last_updated()
    }
    pub fn decrypted_data(d: &DocumentDecryptResult) -> Vec<i8> {
        u8_conv(d.decrypted_data()).to_vec()
    }
}

mod document_decrypt_unmanaged_result {
    use super::*;
    /// Generic translation of ironoxide's UserOrGroup enum
    #[derive(Hash, PartialEq, Eq)]
    pub struct UserOrGroupId {
        id: String,
        is_user: bool,
    }

    impl UserOrGroupId {
        pub fn new(id: String, is_user: bool) -> UserOrGroupId {
            UserOrGroupId { id, is_user }
        }
        pub fn id(&self) -> String {
            self.id.clone()
        }

        pub fn is_user(&self) -> bool {
            self.is_user
        }

        pub fn is_group(&self) -> bool {
            !self.is_user
        }
    }
    pub fn id(d: &DocumentDecryptUnmanagedResult) -> DocumentId {
        d.id().clone()
    }
    pub fn decrypted_data(d: &DocumentDecryptUnmanagedResult) -> Vec<i8> {
        u8_conv(d.decrypted_data()).to_vec()
    }
    pub fn access_via(d: &DocumentDecryptUnmanagedResult) -> UserOrGroupId {
        let (id, is_user) = match d.access_via() {
            UserOrGroup::User { id } => (id.id().to_string(), true),
            UserOrGroup::Group { id } => (id.id().to_string(), false),
        };
        UserOrGroupId::new(id, is_user)
    }
}

// UserAccessErr and GroupAccessErr are a Java-compatible representation of IronOxide's
// DocAccessEditErr. They are encoded this this because this seemed like the most
// straightforward way to represent a error for both a user or group (like UserOrGroup)
#[derive(Clone, Debug, Hash, PartialEq, Eq)]
pub struct UserAccessErr {
    id: UserId,
    err: String,
}

impl UserAccessErr {
    pub fn id(&self) -> UserId {
        self.id.clone()
    }

    pub fn err(&self) -> String {
        self.err.clone()
    }
}

/// Wrap the Vec<UserId> type in a newtype because swig can't handle
/// passing through an Option<Vec<*>> for GroupGetResult
#[derive(Clone, Debug, Hash, PartialEq, Eq)]
pub struct GroupUserList(Vec<UserId>);
impl GroupUserList {
    pub fn list(&self) -> Vec<UserId> {
        self.0.clone()
    }
}

#[derive(Clone, Debug, Hash, PartialEq, Eq)]
pub struct GroupAccessErr {
    id: GroupId,
    err: String,
}

impl GroupAccessErr {
    pub fn id(&self) -> GroupId {
        self.id.clone()
    }

    pub fn err(&self) -> String {
        self.err.clone()
    }
}

mod document_access_change_result {
    use super::*;
    use itertools::{Either, Itertools};

    #[derive(Hash, PartialEq)]
    pub struct SucceededResult {
        users: Vec<UserId>,
        groups: Vec<GroupId>,
    }

    impl SucceededResult {
        pub fn users(&self) -> Vec<UserId> {
            self.users.clone()
        }
        pub fn groups(&self) -> Vec<GroupId> {
            self.groups.clone()
        }
    }

    #[derive(Hash, PartialEq)]
    pub struct FailedResult {
        users: Vec<UserAccessErr>,
        groups: Vec<GroupAccessErr>,
    }

    impl FailedResult {
        pub fn is_empty(&self) -> bool {
            self.users.is_empty() && self.groups.is_empty()
        }

        pub fn users(&self) -> Vec<UserAccessErr> {
            self.users.clone()
        }

        pub fn groups(&self) -> Vec<GroupAccessErr> {
            self.groups.clone()
        }
    }

    pub trait DocumentAccessChange {
        fn changed(&self) -> SucceededResult;
        fn errors(&self) -> FailedResult;
    }

    impl DocumentAccessChange for DocumentAccessResult {
        fn changed(&self) -> SucceededResult {
            to_succeeded_result(self.succeeded())
        }

        fn errors(&self) -> FailedResult {
            to_failed_result(self.failed())
        }
    }

    impl DocumentAccessChange for DocumentEncryptResult {
        fn changed(&self) -> SucceededResult {
            to_succeeded_result(self.grants())
        }

        fn errors(&self) -> FailedResult {
            to_failed_result(self.access_errs())
        }
    }

    impl DocumentAccessChange for DocumentEncryptUnmanagedResult {
        fn changed(&self) -> SucceededResult {
            to_succeeded_result(self.grants())
        }

        fn errors(&self) -> FailedResult {
            to_failed_result(self.access_errs())
        }
    }

    fn to_succeeded_result(successes: &[UserOrGroup]) -> SucceededResult {
        let (users, groups) = successes.iter().cloned().partition_map(|uog| match uog {
            UserOrGroup::User { id } => Either::Left(id),
            UserOrGroup::Group { id } => Either::Right(id),
        });

        SucceededResult { users, groups }
    }

    fn to_failed_result(access_errs: &[DocAccessEditErr]) -> FailedResult {
        let (users, groups) =
            access_errs
                .iter()
                .cloned()
                .partition_map(|access_err| match access_err {
                    DocAccessEditErr {
                        user_or_group: UserOrGroup::User { id },
                        err,
                    } => Either::Left(UserAccessErr { id, err }),
                    DocAccessEditErr {
                        user_or_group: UserOrGroup::Group { id },
                        err,
                    } => Either::Right(GroupAccessErr { id, err }),
                });

        FailedResult { users, groups }
    }
}

mod group_meta_result {
    use super::*;
    pub fn id(g: &GroupMetaResult) -> GroupId {
        g.id().clone()
    }
    pub fn name(g: &GroupMetaResult) -> Option<GroupName> {
        g.name().cloned()
    }
    pub fn created(g: &GroupMetaResult) -> DateTime<Utc> {
        *g.created()
    }
    pub fn last_updated(g: &GroupMetaResult) -> DateTime<Utc> {
        *g.last_updated()
    }
    pub fn needs_rotation(g: &GroupMetaResult) -> Option<NullableBoolean> {
        g.needs_rotation().map(NullableBoolean)
    }
}

mod group_create_result {
    use super::*;
    pub fn id(g: &GroupCreateResult) -> GroupId {
        g.id().clone()
    }
    pub fn name(g: &GroupCreateResult) -> Option<GroupName> {
        g.name().cloned()
    }
    pub fn group_master_public_key(g: &GroupCreateResult) -> PublicKey {
        g.group_master_public_key().clone()
    }
    pub fn owner(g: &GroupCreateResult) -> UserId {
        g.owner().clone()
    }
    pub fn admin_list(g: &GroupCreateResult) -> GroupUserList {
        GroupUserList(g.admins().clone())
    }
    pub fn member_list(g: &GroupCreateResult) -> GroupUserList {
        GroupUserList(g.members().clone())
    }
    pub fn created(g: &GroupCreateResult) -> DateTime<Utc> {
        *g.created()
    }
    pub fn last_updated(g: &GroupCreateResult) -> DateTime<Utc> {
        *g.last_updated()
    }
    pub fn needs_rotation(g: &GroupCreateResult) -> Option<NullableBoolean> {
        g.needs_rotation().map(NullableBoolean)
    }
}

mod group_list_result {
    use super::*;
    pub fn result(g: &GroupListResult) -> Vec<GroupMetaResult> {
        g.result().clone()
    }
}

mod group_get_result {
    use super::*;
    pub fn id(g: &GroupGetResult) -> GroupId {
        g.id().clone()
    }
    pub fn name(g: &GroupGetResult) -> Option<GroupName> {
        g.name().cloned()
    }
    pub fn group_master_public_key(result: &GroupGetResult) -> PublicKey {
        result.group_master_public_key().clone()
    }
    pub fn admin_list(result: &GroupGetResult) -> Option<GroupUserList> {
        result.admin_list().cloned().map(GroupUserList)
    }
    pub fn member_list(result: &GroupGetResult) -> Option<GroupUserList> {
        result.member_list().cloned().map(GroupUserList)
    }
    pub fn created(g: &GroupGetResult) -> DateTime<Utc> {
        *g.created()
    }
    pub fn last_updated(g: &GroupGetResult) -> DateTime<Utc> {
        *g.last_updated()
    }
    pub fn needs_rotation(g: &GroupGetResult) -> Option<NullableBoolean> {
        g.needs_rotation().map(NullableBoolean)
    }
}

mod group_update_private_key_result {
    use super::*;
    pub fn needs_rotation(g: &GroupUpdatePrivateKeyResult) -> bool {
        g.needs_rotation()
    }
    pub fn id(g: &GroupUpdatePrivateKeyResult) -> GroupId {
        g.id().clone()
    }
}

mod group_create_opts {
    use super::*;
    pub fn create(
        id: Option<&GroupId>,
        name: Option<&GroupName>,
        add_as_admin: bool,
        add_as_member: bool,
        owner: Option<&UserId>,
        admins: Vec<UserId>,
        members: Vec<UserId>,
        needs_rotation: bool,
    ) -> GroupCreateOpts {
        GroupCreateOpts::new(
            id.cloned(),
            name.cloned(),
            add_as_admin,
            add_as_member,
            owner.cloned(),
            admins,
            members,
            needs_rotation,
        )
    }
}

mod policy_caching_config {
    use super::*;
    pub fn create(max_entries: usize) -> PolicyCachingConfig {
        PolicyCachingConfig { max_entries }
    }
    pub fn get_max_entries(pcc: &PolicyCachingConfig) -> usize {
        pcc.max_entries
    }
}

mod ironoxide_config {
    use super::*;
    pub fn create(
        policy_caching: &PolicyCachingConfig,
        sdk_operation_timeout: Option<&Duration>,
    ) -> IronOxideConfig {
        IronOxideConfig {
            policy_caching: policy_caching.clone(),
            sdk_operation_timeout: sdk_operation_timeout.copied(),
        }
    }
    pub fn get_policy_caching(ioc: &IronOxideConfig) -> PolicyCachingConfig {
        ioc.policy_caching.clone()
    }
    pub fn get_timeout(ioc: &IronOxideConfig) -> Option<Duration> {
        ioc.sdk_operation_timeout
    }
}

mod duration {
    use super::*;
    pub fn get_millis(d: &Duration) -> u64 {
        d.as_millis() as u64
    }
    pub fn get_secs(d: &Duration) -> u64 {
        d.as_secs()
    }
}

//Java SDK wrapper functions for doing unnatural things with the JNI.
fn user_verify(jwt: &str, timeout: Option<&Duration>) -> Result<Option<UserResult>, String> {
    Ok(IronOxide::user_verify(jwt, timeout.copied())?)
}
fn user_create(
    jwt: &str,
    password: &str,
    opts: &UserCreateOpts,
    timeout: Option<&Duration>,
) -> Result<UserCreateResult, String> {
    Ok(IronOxide::user_create(
        jwt,
        password,
        opts,
        timeout.copied(),
    )?)
}
fn initialize(init: &DeviceContext, config: &IronOxideConfig) -> Result<IronOxide, String> {
    Ok(ironoxide::blocking::initialize(init, config)?)
}
fn initialize_and_rotate(
    init: &DeviceContext,
    password: &str,
    config: &IronOxideConfig,
    timeout: Option<&Duration>,
) -> Result<IronOxide, String> {
    let rotate_timeout = timeout.copied().or(config.sdk_operation_timeout);
    Ok(
        match ironoxide::blocking::initialize_check_rotation(init, config)? {
            InitAndRotationCheck::RotationNeeded(ironoxide, rotation) => {
                ironoxide.rotate_all(&rotation, password, rotate_timeout)?;
                ironoxide
            }
            InitAndRotationCheck::NoRotationNeeded(ironoxide) => ironoxide,
        },
    )
}
fn generate_new_device(
    jwt: &str,
    password: &str,
    opts: &DeviceCreateOpts,
    timeout: Option<&Duration>,
) -> Result<DeviceAddResult, String> {
    Ok(IronOxide::generate_new_device(
        jwt,
        password,
        opts,
        timeout.copied(),
    )?)
}
fn user_list_devices(sdk: &IronOxide) -> Result<UserDeviceListResult, String> {
    Ok(sdk.user_list_devices()?)
}
fn user_get_public_key(sdk: &IronOxide, users: Vec<UserId>) -> Result<Vec<UserWithKey>, String> {
    let result = sdk.user_get_public_key(&users)?;
    Ok(result.into_iter().map(UserWithKey).collect())
}
fn user_delete_device(sdk: &IronOxide, device_id: Option<&DeviceId>) -> Result<DeviceId, String> {
    Ok(sdk.user_delete_device(device_id)?)
}
fn user_rotate_private_key(
    sdk: &IronOxide,
    password: &str,
) -> Result<UserUpdatePrivateKeyResult, String> {
    Ok(sdk.user_rotate_private_key(password)?)
}
fn document_list(sdk: &IronOxide) -> Result<DocumentListResult, String> {
    Ok(sdk.document_list()?)
}
fn document_get_metadata(
    sdk: &IronOxide,
    id: &DocumentId,
) -> Result<DocumentMetadataResult, String> {
    Ok(sdk.document_get_metadata(id)?)
}
fn document_get_id_from_bytes(sdk: &IronOxide, bytes: &[i8]) -> Result<DocumentId, String> {
    Ok(sdk.document_get_id_from_bytes(i8_conv(bytes))?)
}
fn document_encrypt(
    sdk: &IronOxide,
    data: &[i8],
    opts: &DocumentEncryptOpts,
) -> Result<DocumentEncryptResult, String> {
    Ok(sdk.document_encrypt(i8_conv(data), opts)?)
}
fn document_update_bytes(
    sdk: &IronOxide,
    document_id: &DocumentId,
    data: &[i8],
) -> Result<DocumentEncryptResult, String> {
    Ok(sdk.document_update_bytes(document_id, i8_conv(data))?)
}
fn document_decrypt(sdk: &IronOxide, data: &[i8]) -> Result<DocumentDecryptResult, String> {
    Ok(sdk.document_decrypt(i8_conv(data))?)
}
fn document_update_name(
    sdk: &IronOxide,
    document_id: &DocumentId,
    name: Option<&DocumentName>,
) -> Result<DocumentMetadataResult, String> {
    Ok(sdk.document_update_name(document_id, name)?)
}

fn document_grant_access(
    sdk: &IronOxide,
    document_id: &DocumentId,
    grant_users: Vec<UserId>,
    grant_groups: Vec<GroupId>,
) -> Result<DocumentAccessResult, String> {
    let users_and_groups = grant_users
        .into_iter()
        .map(|u| UserOrGroup::User { id: u })
        .chain(
            grant_groups
                .into_iter()
                .map(|g| UserOrGroup::Group { id: g }),
        )
        .collect();

    Ok(sdk.document_grant_access(document_id, &users_and_groups)?)
}

fn document_revoke_access(
    sdk: &IronOxide,
    document_id: &DocumentId,
    revoke_users: Vec<UserId>,
    revoke_groups: Vec<GroupId>,
) -> Result<DocumentAccessResult, String> {
    let users_and_groups = revoke_users
        .into_iter()
        .map(|u| UserOrGroup::User { id: u })
        .chain(
            revoke_groups
                .into_iter()
                .map(|g| UserOrGroup::Group { id: g }),
        )
        .collect();

    Ok(sdk.document_revoke_access(document_id, &users_and_groups)?)
}
fn group_list(sdk: &IronOxide) -> Result<GroupListResult, String> {
    Ok(sdk.group_list()?)
}
fn group_get_metadata(sdk: &IronOxide, id: &GroupId) -> Result<GroupGetResult, String> {
    Ok(sdk.group_get_metadata(id)?)
}
fn group_create(sdk: &IronOxide, opts: &GroupCreateOpts) -> Result<GroupCreateResult, String> {
    Ok(sdk.group_create(opts)?)
}
fn group_update_name(
    sdk: &IronOxide,
    id: &GroupId,
    name: Option<&GroupName>,
) -> Result<GroupMetaResult, String> {
    Ok(sdk.group_update_name(id, name)?)
}
fn group_delete(sdk: &IronOxide, id: &GroupId) -> Result<GroupId, String> {
    Ok(sdk.group_delete(id)?)
}
fn group_add_members(
    sdk: &IronOxide,
    group_id: &GroupId,
    users: Vec<UserId>,
) -> Result<GroupAccessEditResult, String> {
    Ok(sdk.group_add_members(group_id, &users)?)
}
fn group_remove_members(
    sdk: &IronOxide,
    group_id: &GroupId,
    users: Vec<UserId>,
) -> Result<GroupAccessEditResult, String> {
    Ok(sdk.group_remove_members(group_id, &users)?)
}
fn group_add_admins(
    sdk: &IronOxide,
    group_id: &GroupId,
    users: Vec<UserId>,
) -> Result<GroupAccessEditResult, String> {
    Ok(sdk.group_add_admins(group_id, &users)?)
}
fn group_remove_admins(
    sdk: &IronOxide,
    group_id: &GroupId,
    users: Vec<UserId>,
) -> Result<GroupAccessEditResult, String> {
    Ok(sdk.group_remove_admins(group_id, &users)?)
}
fn group_rotate_private_key(
    sdk: &IronOxide,
    group_id: &GroupId,
) -> Result<GroupUpdatePrivateKeyResult, String> {
    Ok(sdk.group_rotate_private_key(group_id)?)
}

fn advanced_document_encrypt_unmanaged(
    sdk: &IronOxide,
    data: &[i8],
    opts: &DocumentEncryptOpts,
) -> Result<DocumentEncryptUnmanagedResult, String> {
    Ok(sdk.document_encrypt_unmanaged(i8_conv(data), opts)?)
}

fn advanced_document_decrypt_unmanaged(
    sdk: &IronOxide,
    encrypted_data: &[i8],
    encrypted_deks: &[i8],
) -> Result<DocumentDecryptUnmanagedResult, String> {
    Ok(sdk.document_decrypt_unmanaged(i8_conv(encrypted_data), i8_conv(encrypted_deks))?)
}
mod group_access_edit_result {
    use super::*;
    pub fn succeeded(result: &GroupAccessEditResult) -> Vec<UserId> {
        result.succeeded().clone()
    }

    pub fn failed(result: &GroupAccessEditResult) -> Vec<GroupAccessEditErr> {
        result.failed().to_vec()
    }
}

mod access_edit_failure {
    use super::*;
    pub fn user(result: &GroupAccessEditErr) -> UserId {
        result.user().clone()
    }

    pub fn error(result: &GroupAccessEditErr) -> String {
        result.error().clone()
    }
}
