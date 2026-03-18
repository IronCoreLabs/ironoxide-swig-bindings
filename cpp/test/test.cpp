#include "acutest.h"
#include <cstdlib>
#include <cstdio>
#include <fstream>
#include <iostream>
#include <random>
#include "IronOxide.hpp"
using namespace sdk;

template <class T>
T unwrap(std::variant<T, RustString> value)
{
    if (value.index() == 0)
    {
        return std::get<T>(std::move(value));
    }
    else
    {
        std::cout << "Got fatal error: " << std::get<RustString>(value).to_string_view() << "\n";
        exit(EXIT_FAILURE);
    }
}

RustSlice<const int8_t> string_to_slice(std::string str)
{
    return RustSlice{reinterpret_cast<const int8_t *>(str.data()), str.size()};
}

RustSlice<const int8_t> vec_to_slice(RustVeci8 a)
{
    return RustSlice{&a[0], a.size()};
}

std::string vec_to_string(RustVeci8 a)
{
    return std::string(reinterpret_cast<char const *>(&a[0]), a.size());
}

std::string random_string(std::size_t length)
{
    const std::string CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    std::random_device random_device;
    std::mt19937 generator(random_device());
    std::uniform_int_distribution<> distribution(0, CHARACTERS.size() - 1);

    std::string random_string;

    for (std::size_t i = 0; i < length; ++i)
    {
        random_string += CHARACTERS[distribution(generator)];
    }

    return random_string;
}
std::string random_id()
{
    return random_string(94);
}

auto deviceContextString = "{\"accountId\": \"test-user\",\"segmentId\": 2546,\"signingPrivateKey\": \"qqoar4KHf9GVBv0a8EQxVx8GJ08FdOY1/wz/LdfDHRP+gyfBTxWoVWA2/6SgXrRq7uWGhdJ9txnujLBDXz2A0A==\",\"devicePrivateKey\": \"GbvMdMLTmVNQHRVC/TT06abJG8VoScWBwA2+m/b7nRY=\"}";

void test_user_id(void)
{
    auto str = "hello";
    auto value = UserId::validate(str);
    // These don't seem to work and I don't know why.
    auto user_id = unwrap(std::move(value));

    // This is to check that equals is defined and that it's working.
    TEST_CHECK(user_id == user_id);
    TEST_CHECK(user_id.getId().to_std_string() == "hello");
}

void test_user_id_error(void)
{
    auto value = UserId::validate("hello*^");
    TEST_CHECK(value.index() == 1);
    auto rust_error_message = std::get<1>(value);
    auto error_message = rust_error_message.to_std_string();
    TEST_CHECK(error_message.length() > 10);
    TEST_MSG("Error was: %s", error_message.c_str());
}

void encrypt_decrypt_roundtrip(void)
{
    DeviceContext d = unwrap(DeviceContext::fromJsonString(deviceContextString));
    IronOxide sdk = unwrap(IronOxide::initialize(d, IronOxideConfig()));
    auto encrypted_doc = unwrap(sdk.documentEncrypt(string_to_slice("foo"), DocumentEncryptOpts()));
    auto decrypted = unwrap(sdk.documentDecrypt(vec_to_slice(encrypted_doc.getEncryptedData())));
    TEST_CHECK(vec_to_string(decrypted.getDecryptedData()) == "foo");
    TEST_MSG("Decrypted value is not what was encrypted.");

    TEST_CHECK(decrypted.getId().getId().to_std_string().length() == 32);
    TEST_CHECK(!decrypted.getName().has_value());
    TEST_CHECK(decrypted.getCreated() == encrypted_doc.getCreated());
    TEST_CHECK(decrypted.getLastUpdated() == encrypted_doc.getLastUpdated());
}

void group_name(void)
{
    auto group_name = unwrap(GroupName::validate("blargh"));
    TEST_CHECK(group_name.getName().to_std_string() == "blargh");
}

void group_create_default(void)
{
    DeviceContext d = unwrap(DeviceContext::fromJsonString(deviceContextString));
    IronOxide sdk = unwrap(IronOxide::initialize(d, IronOxideConfig()));
    auto group_create_result = unwrap(sdk.groupCreate(GroupCreateOpts()));
    TEST_CHECK(group_create_result.getMemberList().getList().as_slice().size() == 1);
    TEST_MSG("Group create failed.");
}

void group_create_passing_args(void)
{
    DeviceContext d = unwrap(DeviceContext::fromJsonString(deviceContextString));
    IronOxide sdk = unwrap(IronOxide::initialize(d, IronOxideConfig()));
    auto group_name = unwrap(GroupName::validate(random_id()));
    auto group_id = unwrap(GroupId::validate(random_id()));
    auto creator = d.getAccountId();
    auto group_create_result = unwrap(sdk.groupCreate(GroupCreateOpts(&group_id, &group_name, true, true, &creator, RustForeignSliceConst<UserIdRef>(), RustForeignSliceConst<UserIdRef>(), false)));
    auto member_size = group_create_result.getMemberList().getList().as_slice().size();
    TEST_CHECK_(member_size == 1, "Group member list size is %d", member_size);
    TEST_CHECK_(group_create_result.isAdmin(), "We should be an admin.");
    TEST_CHECK_(group_create_result.isMember(), "We should be a member.");
    auto admin_size = group_create_result.getAdminList().getList().as_slice().size();
    TEST_CHECK_(admin_size == 1, "Admin size was %d, but should be 1.", admin_size);
    TEST_CHECK_(!group_create_result.getNeedsRotation().value().getBoolean(), "Group should not need rotation.");
    TEST_CHECK_(group_create_result.getMemberList() == group_create_result.getMemberList(), "Member lists should be equal.");
}

// This test is just a confirmation that passing nulls works, so we don't assert about much in it.
void group_create_passing_nulls(void)
{
    DeviceContext d = unwrap(DeviceContext::fromJsonString(deviceContextString));
    IronOxide sdk = unwrap(IronOxide::initialize(d, IronOxideConfig()));
    auto group_create_result = unwrap(sdk.groupCreate(GroupCreateOpts(nullptr, nullptr, true, true, nullptr, RustForeignSliceConst<UserIdRef>(), RustForeignSliceConst<UserIdRef>(), false)));
    auto group_member_size = group_create_result.getMemberList().getList().as_slice().size();
    TEST_CHECK_(group_member_size == 1, "Group member list should be 1, but was %d", group_member_size);
}

void group_list(void)
{
    DeviceContext d = unwrap(DeviceContext::fromJsonString(deviceContextString));
    IronOxide sdk = unwrap(IronOxide::initialize(d, IronOxideConfig()));
    auto group_list_result = unwrap(sdk.groupList());
    // We don't create new users in these tests, so all we can do is assert that there is some.
    TEST_CHECK_(group_list_result.getResult().as_slice().size() > 1, "Group list failed.");
}

//{
// "sub": "abcABC012_.$#|@/:;=+'-d1226d1b-4c39-49da-933c-642e23ac1945",
// "pid": 438,
// "sid": "ironoxide-dev1",
// "kid": 593,
// "iat": 1591901740,
// "exp": 1591901860
// }
void jwt_test_no_prefixes(void)
{
    Jwt j = unwrap(Jwt::validate("eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJhYmNBQkMwMTJfLiQjfEAvOjs9KyctZDEyMjZkMWItNGMzOS00OWRhLTkzM2MtNjQyZTIzYWMxOTQ1IiwicGlkIjo0MzgsInNpZCI6Imlyb25veGlkZS1kZXYxIiwia2lkIjo1OTMsImlhdCI6MTU5MTkwMTc0MCwiZXhwIjoxNTkxOTAxODYwfQ.wgs_tnh89SlKnIkoQHdlC0REjkxTl1P8qtDSQwWTFKwo8KQKXUQdpp4BfwqUqLcxA0BW6_XfVRlqMX5zcvCc6w"));
    TEST_CHECK_(j.getAlgorithm().to_std_string() == "ES256", "Wrong jwt algorithm");
    JwtClaims claims = j.getClaims();
    TEST_CHECK_(claims.getSub().to_std_string() == "abcABC012_.$#|@/:;=+'-d1226d1b-4c39-49da-933c-642e23ac1945", "Wrong jwt sub");
    TEST_CHECK_(claims.getPid() == 438, "Wrong jwt pid");
    TEST_CHECK_(claims.getPrefixedPid().has_value() == false, "Wrong jwt prefixed pid");
    TEST_CHECK_(claims.getSid().value().to_std_string() == "ironoxide-dev1", "Wrong jwt sid");
    TEST_CHECK_(claims.getPrefixedSid().has_value() == false, "Wrong jwt prefixed sid");
    TEST_CHECK_(claims.getKid() == 593, "Wrong jwt kid");
    TEST_CHECK_(claims.getPrefixedKid().has_value() == false, "Wrong jwt prefixed kid");
    TEST_CHECK_(claims.getUid().has_value() == false, "Wrong jwt uid");
    TEST_CHECK_(claims.getPrefixedUid().has_value() == false, "Wrong jwt prefixed uid");
    TEST_CHECK_(claims.getIat() == 1591901740, "Wrong jwt iat");
    TEST_CHECK_(claims.getExp() == 1591901860, "Wrong jwt exp");
}

//{
// "sub": "abcABC012_.$#|@/:;=+'-d1226d1b-4c39-49da-933c-642e23ac1945",
// "http://ironcore/pid": 438,
// "http://ironcore/sid": "ironoxide-dev1",
// "http://ironcore/kid": 593,
// "iat": 1591901740,
// "exp": 1591901860
// }
void jwt_test_prefixes(void)
{
    Jwt j = unwrap(Jwt::validate("eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhYmNBQkMwMTJfLiQjfEAvOjs9KyctZDEyMjZkMWItNGMzOS00OWRhLTkzM2MtNjQyZTIzYWMxOTQ1IiwiaHR0cDovL2lyb25jb3JlL3BpZCI6NDM4LCJodHRwOi8vaXJvbmNvcmUvc2lkIjoiaXJvbm94aWRlLWRldjEiLCJodHRwOi8vaXJvbmNvcmUva2lkIjo1OTMsImlhdCI6MTU5MTkwMTc0MCwiZXhwIjoxNTkxOTAxODYwfQ.bCIDkN6bXaz85pl9s55MoAByzm0LPlMPlT5WqjT-R6F80EKFO0gqGT1m7330gxnN-LWtxonBVv1IoK9tl-NEvg"));
    TEST_CHECK_(j.getAlgorithm().to_std_string() == "ES256", "Wrong jwt algorithm");
    JwtClaims claims = j.getClaims();
    TEST_CHECK_(claims.getSub().to_std_string() == "abcABC012_.$#|@/:;=+'-d1226d1b-4c39-49da-933c-642e23ac1945", "Wrong jwt sub");
    TEST_CHECK_(claims.getPrefixedPid() == 438, "Wrong jwt prefixed pid");
    TEST_CHECK_(claims.getPid().has_value() == false, "Wrong jwt pid");
    TEST_CHECK_(claims.getPrefixedSid().value().to_std_string() == "ironoxide-dev1", "Wrong jwt prefixed sid");
    TEST_CHECK_(claims.getSid().has_value() == false, "Wrong jwt sid");
    TEST_CHECK_(claims.getPrefixedKid() == 593, "Wrong jwt prefixed kid");
    TEST_CHECK_(claims.getKid().has_value() == false, "Wrong jwt kid");
    TEST_CHECK_(claims.getPrefixedUid().has_value() == false, "Wrong jwt prefixed uid");
    TEST_CHECK_(claims.getUid().has_value() == false, "Wrong jwt uid");
    TEST_CHECK_(claims.getIat() == 1591901740, "Wrong jwt iat");
    TEST_CHECK_(claims.getExp() == 1591901860, "Wrong jwt exp");
}

void unmanaged_encrypt_decrypt_roundtrip(void)
{
    DeviceContext d = unwrap(DeviceContext::fromJsonString(deviceContextString));
    IronOxide sdk = unwrap(IronOxide::initialize(d, IronOxideConfig()));
    auto encrypted = unwrap(sdk.documentEncryptUnmanaged(string_to_slice("unmanaged test"), DocumentEncryptOpts()));
    TEST_CHECK_(encrypted.getId().getId().to_std_string().length() == 32, "Document ID should be 32 chars.");
    TEST_CHECK_(encrypted.getEncryptedData().size() > 0, "Encrypted data should be non-empty.");
    TEST_CHECK_(encrypted.getEncryptedDeks().size() > 0, "Encrypted DEKs should be non-empty.");

    auto decrypted = unwrap(sdk.documentDecryptUnmanaged(vec_to_slice(encrypted.getEncryptedData()), vec_to_slice(encrypted.getEncryptedDeks())));
    TEST_CHECK_(vec_to_string(decrypted.getDecryptedData()) == "unmanaged test", "Decrypted data should match original.");
    TEST_CHECK_(decrypted.getId() == encrypted.getId(), "Document IDs should match.");
}

void unmanaged_metadata_and_id(void)
{
    DeviceContext d = unwrap(DeviceContext::fromJsonString(deviceContextString));
    IronOxide sdk = unwrap(IronOxide::initialize(d, IronOxideConfig()));
    auto encrypted = unwrap(sdk.documentEncryptUnmanaged(string_to_slice("metadata test"), DocumentEncryptOpts()));

    // get metadata from EDEKs
    auto metadata = unwrap(sdk.documentGetMetadataUnmanaged(vec_to_slice(encrypted.getEncryptedDeks())));
    TEST_CHECK_(metadata.getId() == encrypted.getId(), "Metadata ID should match encrypt result ID.");
    TEST_CHECK_(metadata.getVisibleToUsers().as_slice().size() >= 1, "Should have at least one visible user.");

    // get ID from encrypted bytes
    auto id_from_bytes = unwrap(sdk.documentGetIdFromBytesUnmanaged(vec_to_slice(encrypted.getEncryptedData())));
    TEST_CHECK_(id_from_bytes == encrypted.getId(), "ID from bytes should match.");

    // get ID from EDEKs
    auto id_from_edeks = unwrap(sdk.documentGetIdFromEdeksUnmanaged(vec_to_slice(encrypted.getEncryptedDeks())));
    TEST_CHECK_(id_from_edeks == encrypted.getId(), "ID from EDEKs should match.");
}

void unmanaged_grant_access(void)
{
    DeviceContext d = unwrap(DeviceContext::fromJsonString(deviceContextString));
    IronOxide sdk = unwrap(IronOxide::initialize(d, IronOxideConfig()));
    auto encrypted = unwrap(sdk.documentEncryptUnmanaged(string_to_slice("grant test"), DocumentEncryptOpts()));

    // grant access to self (already has access, but exercises the API)
    // flapigen's RustForeignSlice::operator[] casts (data + step*i) directly to const CForeignType*,
    // so data must point to the opaque object itself, not to a pointer-to-pointer.
    auto self_id = d.getAccountId();
    CRustObjectSlice user_slice = { static_cast<void *>(static_cast<UserIdOpaque *>(self_id)), 1, 1 };
    auto grant_result = unwrap(sdk.documentGrantAccessUnmanaged(
        vec_to_slice(encrypted.getEncryptedDeks()),
        RustForeignSliceConst<UserIdRef>(user_slice),
        RustForeignSliceConst<GroupIdRef>()));
    TEST_CHECK_(grant_result.getEncryptedDeks().size() > 0, "Updated EDEKs should be non-empty.");
    TEST_CHECK_(grant_result.getErrors().isEmpty(), "There should be no errors.");
    TEST_CHECK_(grant_result.getAccessViaUserOrGroup().has_value(), "Access via should be present for grant.");

    // verify we can still decrypt with the updated EDEKs
    auto decrypted = unwrap(sdk.documentDecryptUnmanaged(vec_to_slice(encrypted.getEncryptedData()), vec_to_slice(grant_result.getEncryptedDeks())));
    TEST_CHECK_(vec_to_string(decrypted.getDecryptedData()) == "grant test", "Decrypted data should match after grant.");
}

void export_reimport_public_key_cache(void)
{
    DeviceContext d = unwrap(DeviceContext::fromJsonString(deviceContextString));
    IronOxide sdk = unwrap(IronOxide::initialize(d, IronOxideConfig()));
    auto cache_bytes = unwrap(sdk.exportPublicKeyCache());
    TEST_CHECK_(cache_bytes.size() > 0, "Cache should be non-empty, but was %zu", cache_bytes.size());

    // Re-initialize with the exported cache
    DeviceContext d2 = unwrap(DeviceContext::fromJsonString(deviceContextString));
    IronOxide sdk2 = unwrap(IronOxide::initializeWithPublicKeys(d2, IronOxideConfig(), RustSlice<const int8_t>{&cache_bytes[0], cache_bytes.size()}));

    // Verify the new SDK is functional
    auto doc_list = unwrap(sdk2.documentList());
    TEST_CHECK_(doc_list.getResult().as_slice().size() >= 0, "Should be able to list documents after reinit with cache.");
}

void file_encrypt_decrypt_unmanaged_roundtrip(void)
{
    DeviceContext d = unwrap(DeviceContext::fromJsonString(deviceContextString));
    IronOxide sdk = unwrap(IronOxide::initialize(d, IronOxideConfig()));

    // Create temp file paths
    std::string source_path = "/tmp/ironoxide_cpp_test_source.txt";
    std::string encrypted_path = "/tmp/ironoxide_cpp_test_encrypted.iron";
    std::string decrypted_path = "/tmp/ironoxide_cpp_test_decrypted.txt";

    // Write test data to source file
    std::string test_data = "Hello, streaming encryption from C++!";
    {
        std::ofstream out(source_path, std::ios::binary);
        out.write(test_data.data(), test_data.size());
    }

    // Encrypt file
    auto encrypt_result = unwrap(sdk.documentFileEncryptUnmanaged(source_path.c_str(), encrypted_path.c_str(), DocumentEncryptOpts()));
    TEST_CHECK_(encrypt_result.getId().getId().to_std_string().length() == 32, "Document ID should be 32 chars.");
    TEST_CHECK_(encrypt_result.getEncryptedDeks().size() > 0, "Encrypted DEKs should be non-empty.");

    // Decrypt file
    auto decrypt_result = unwrap(sdk.documentFileDecryptUnmanaged(
        encrypted_path.c_str(),
        decrypted_path.c_str(),
        vec_to_slice(encrypt_result.getEncryptedDeks())));
    TEST_CHECK_(decrypt_result.getId() == encrypt_result.getId(), "Document IDs should match.");

    // Read decrypted file and verify contents
    std::ifstream in(decrypted_path, std::ios::binary);
    std::string decrypted_data((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
    TEST_CHECK_(decrypted_data == test_data, "Decrypted file content should match original.");

    // Cleanup temp files
    std::remove(source_path.c_str());
    std::remove(encrypted_path.c_str());
    std::remove(decrypted_path.c_str());
}

TEST_LIST = {
    {"test_user_id", test_user_id},
    {"test_user_id_error", test_user_id_error},
    {"encrypt_decrypt_roundtrip", encrypt_decrypt_roundtrip},
    {"group_name", group_name},
    {"group_create_default", group_create_default},
    {"group_create_passing_args", group_create_passing_args},
    {"group_create_passing_nulls", group_create_passing_nulls},
    {"group_list", group_list},
    {"jwt_test", jwt_test_no_prefixes},
    {"jwt_test", jwt_test_prefixes},
    {"unmanaged_encrypt_decrypt_roundtrip", unmanaged_encrypt_decrypt_roundtrip},
    {"unmanaged_metadata_and_id", unmanaged_metadata_and_id},
    {"unmanaged_grant_access", unmanaged_grant_access},
    {"export_reimport_public_key_cache", export_reimport_public_key_cache},
    {"file_encrypt_decrypt_unmanaged_roundtrip", file_encrypt_decrypt_unmanaged_roundtrip},
    {NULL, NULL}};
