#pragma once

class ParseTransferFile {
public:
    ParseTransferFile(SOCKET socket);
    ~ParseTransferFile();

    void clear();
    void pushData(const uint8_t* data, int len);

private:
    void parseCommands();
    void dispatch(OpCode op, const std::string& payload);

    std::string trim(const std::string& str);
    std::string m_inputBuffer;
    SOCKET m_socket;
};
