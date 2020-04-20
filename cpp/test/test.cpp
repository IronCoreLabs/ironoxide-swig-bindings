#include "acutest.h"
#include "UserId.hpp"
#include <cstdlib>
#include <iostream>
#include <random>
#include <bits/stdc++.h>
#include "UserId_impl.hpp"
#include "DocumentListMeta.hpp"
#include "DocumentMetadataResult.hpp"
#include "IronOxide_fwd.hpp"
#include "IronOxide_impl.hpp"
#include "DeviceContext_impl.hpp"
#include "DocumentEncryptResult_impl.hpp"
#include "DocumentDecryptResult_impl.hpp"
#include "GroupCreateOpts_impl.hpp"
#include "GroupCreateResult_impl.hpp"
#include "GroupUserList_impl.hpp"
#include "GroupId_impl.hpp"
#include "GroupName_impl.hpp"
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

std::string random_string(size_t length)
{
    auto randchar = []() -> char {
        const char charset[] =
            "0123456789"
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            "abcdefghijklmnopqrstuvwxyz";
        const size_t max_index = (sizeof(charset) - 1);
        return charset[rand() % max_index];
    };
    std::string str(length, 0);
    std::generate_n(str.begin(), length, randchar);
    return str;
}

std::string random_id()
{
    return random_string(90);
}

auto deviceContextString = "{\"accountId\":\"abcABC012_.$#|@/:;=+'-91e078f0-a60c-4251-8652-dd498c07a8f4\",\"segmentId\":1825,\"signingPrivateKey\":\"uKHa70uwLVG3IU7XodT2kla/PuC/En8PkRCjMMc9ZE7HFrOV+g0vOwATp/CiXp65mVas0K6TSl/RaxDGlcmsnA==\",\"devicePrivateKey\":\"YZRlDSkM+JxxSXCtWCVK693qfhNqcbhaPrtHs92uD4w=\"}";

void test_user_id(void)
{
    auto str = "hello";
    auto value = UserId::validate(str);
    //These don't seem to work and I don't know why.
    auto user_id = unwrap(std::move(value));

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
    auto group_create_result = unwrap(sdk.groupCreate(GroupCreateOpts(nullptr, nullptr, true, true, nullptr, RustForeignVecUserId(), RustForeignVecUserId(), false)));
    TEST_CHECK(group_create_result.getMemberList().getList().as_slice().size() == 1);
    TEST_MSG("Group create failed.");
}

TEST_LIST = {
    {"test_user_id", test_user_id},
    {"test_user_id_error", test_user_id_error},
    {"encrypt_decrypt_roundtrip", encrypt_decrypt_roundtrip},
    {"group_name", group_name},
    {"group_create_default", group_create_default},
    {"group_create_passing_args", group_create_passing_args},
    {NULL, NULL}};