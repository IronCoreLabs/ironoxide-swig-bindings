#include "acutest.h"
#include "UserId.hpp"
#include <cstdlib>
#include <iostream>
#include <boost/variant.hpp>
#include <boost/utility/string_view.hpp>
#include "DocumentListMeta.hpp"
#include "DocumentMetadataResult.hpp"
#include "IronOxide_fwd.hpp"
#include "IronOxide.hpp"
#include "DeviceContext_impl.hpp"
using namespace sdk;

template <class T>
T unwrap(boost::variant<T, RustString> value)
{
    if (value.which() == 0)
    {
        return boost::get<T>(std::move(value));
    }
    else
    {
        std::cout << "Got fatal error: " << boost::get<RustString>(value).to_boost_string_view() << "\n";
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

void test_user_id(void)
{
    auto foo = AssociationType::FromGroup;
    TEST_CHECK(foo == AssociationType::FromUser);
    // boost::string_view str = "hello";
    // auto value = UserId::validate(str);
    // auto index = value.index();
    // TEST_CHECK(index == 0);
    // //These don't seem to work and I don't know why.
    // auto user_id = std::get<0>(std::move(value));

    // TEST_CHECK(user_id.getId().to_std_string() == "hello");
}

void test_user_id_error(void)
{
    // auto value = UserId::validate("hello*^");
    // TEST_CHECK(value.index() == 1);
    // auto rust_error_message = std::get<1>(value);
    // auto error_message = rust_error_message.to_std_string();
    // TEST_CHECK(error_message.length() > 10);
    // TEST_MSG("Error was: %s", error_message.c_str());
}

void encrypt_decrypt_roundtrip(void)
{
    auto s = boost::string_view("{\"accountId\":\"abcABC012_.$#|@/:;=+'-91e078f0-a60c-4251-8652-dd498c07a8f4\",\"segmentId\":1825,\"signingPrivateKey\":\"uKHa70uwLVG3IU7XodT2kla/PuC/En8PkRCjMMc9ZE7HFrOV+g0vOwATp/CiXp65mVas0K6TSl/RaxDGlcmsnA==\",\"devicePrivateKey\":\"YZRlDSkM+JxxSXCtWCVK693qfhNqcbhaPrtHs92uD4w=\"}");
    DeviceContext d = unwrap(DeviceContext::fromJsonString(s));
    // IronOxide sdk = unwrap(IronOxide::initialize(d, IronOxideConfig()));
    // auto foo = unwrap(sdk.documentEncrypt(string_to_slice("foo"), DocumentEncryptOpts()));
}

TEST_LIST = {
    {"test_user_id", test_user_id},
    {"test_user_id_error", test_user_id_error},
    {"encrypt_decrypt_roundtrip", encrypt_decrypt_roundtrip},
    {NULL, NULL}};