#include "acutest.h"
#include <cstdlib>
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
    //These don't seem to work and I don't know why.
    auto user_id = unwrap(std::move(value));

    //This is to check that equals is defined and that it's working.
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

//This test is just a confirmation that passing nulls works, so we don't assert about much in it.
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
    //We don't create new users in these tests, so all we can do is assert that there is some.
    TEST_CHECK_(group_list_result.getResult().as_slice().size() > 1, "Group list failed.");
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
    {NULL, NULL}};
