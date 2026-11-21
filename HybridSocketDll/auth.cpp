#include "pch.h"
#include "auth.h"
#include "sessioncontext.h"

namespace Auth {

    // ==================== INTERNAL MD5 ====================
    static std::string md5Internal(const std::string& input) {
        if (input.empty()) return "";

        HCRYPTPROV hProv = NULL;
        HCRYPTHASH hHash = NULL;
        BYTE hash[16];
        DWORD hashLen = 16;

        if (!CryptAcquireContext(&hProv, NULL, NULL, PROV_RSA_FULL, CRYPT_VERIFYCONTEXT)) {
            return "";
        }

        if (!CryptCreateHash(hProv, CALG_MD5, 0, 0, &hHash)) {
            CryptReleaseContext(hProv, 0);
            return "";
        }

        if (!CryptHashData(hHash, (const BYTE*)input.c_str(), (DWORD)input.length(), 0)) {
            CryptDestroyHash(hHash);
            CryptReleaseContext(hProv, 0);
            return "";
        }

        if (!CryptGetHashParam(hHash, HP_HASHVAL, hash, &hashLen, 0)) {
            CryptDestroyHash(hHash);
            CryptReleaseContext(hProv, 0);
            return "";
        }

        CryptDestroyHash(hHash);
        CryptReleaseContext(hProv, 0);

        char buf[33] = { 0 };
        for (DWORD i = 0; i < hashLen; i++) {
            sprintf_s(buf + i * 2, 3, "%02x", hash[i]);
        }

        return std::string(buf);
    }

    // ==================== PUBLIC FUNCTIONS ====================

    void setPassword(const std::string& plainTextPassword) {
        if (plainTextPassword.empty()) {
            g_state.needPassword.store(false);
            g_state.passwordMd5.clear();
            return;
        }
        g_state.passwordMd5 = md5Internal(plainTextPassword);
        g_state.needPassword.store(true);
    }

    bool verify(const std::string& md5Hash) {
        if (!g_state.needPassword.load()) {
            return true;
        }
        if (g_state.passwordMd5.empty()) {
            return false;
        }
        return md5Hash == g_state.passwordMd5;
    }

    bool isRequired() {
        return g_state.needPassword.load();
    }

    void clear() {
        g_state.needPassword.store(false);
        g_state.passwordMd5.clear();
    }

    bool hasPassword() {
        return !g_state.passwordMd5.empty();
    }

    std::string getPasswordMD5() {
        return g_state.passwordMd5;
    }

    std::string md5(const std::string& input) {
        return md5Internal(input);
    }

} // namespace Auth