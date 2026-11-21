#pragma once

class TaskManager {
public:
    static std::string getOneTimeSnapshot();
    static bool killProcess(int pid);
    static void init();
    static void cleanup();
};

