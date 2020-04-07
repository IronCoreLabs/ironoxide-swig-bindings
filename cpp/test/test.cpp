#include "acutest.h"
#include "UserId.hpp"
#include <cstdlib>
#include <iostream>
#include "IronOxideConfig.hpp"
using namespace sdk;

void test_user_id(void)
{
    auto value = UserId::validate("hello");
    auto index = value.index();
    TEST_CHECK(index == 0);
    //These don't seem to work and I don't know why.
    auto user_id = std::get<0>(std::move(value));

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

void create_ironoxide_config(void)
{
    // auto foo = IronOxideConfig();
    // TEST_CHECK(!foo.getSdkOperationTimeout().has_value);
}

TEST_LIST = {
    {"test_user_id", test_user_id},
    {"test_user_id_error", test_user_id_error},
    {NULL, NULL}};