#pragma once

namespace Auth {

    // ===== PASSWORD OPERATIONS =====
    void setPassword(const std::string& plainTextPassword);
    bool verify(const std::string& md5Hash);
    bool isRequired();
    void clear();
    bool hasPassword();
    std::string getPasswordMD5();

    // ===== UTILITY =====
    std::string md5(const std::string& input);

} // namespace Auth