#include "pch.h"
#include "taskmanager.h"


// ==================== STATIC VARIABLES ====================
static PDH_HQUERY cpuQuery = NULL;
static PDH_HCOUNTER cpuTotal = NULL;
static ULONGLONG g_lastTotalRamKB = 0;
static bool g_initialized = false;

// ==================== CONFIGURATION ====================
static const int MAX_PROCESSES = 30;
static const double MIN_CPU_PERCENT = 0.1;
static const double MIN_RAM_MB = 0.1;

// ==================== IMPORTANT PROCESSES ====================
static const char* IMPORTANT_PROCESSES[] = {
    "explorer.exe", "chrome.exe", "firefox.exe", "edge.exe",
    "code.exe", "qtcreator.exe", "Taskmgr.exe", "notepad.exe",
    "winword.exe", "excel.exe", "powerpnt.exe", "outlook.exe",
    "discord.exe", "spotify.exe", "slack.exe", "telegram.exe",
    "zoom.exe", "teamviewer.exe", "anydesk.exe",
    nullptr
};

// ==================== CPU SAMPLING ====================
struct CpuSample {
    ULONGLONG lastTime = 0;
    ULONGLONG lastCpu = 0;
};
static std::vector<CpuSample> g_cpuSamples;

// ==================== HELPER FUNCTIONS ====================

static std::string toLower(const std::string& str) {
    std::string result = str;
    for (char& c : result) {
        if (c >= 'A' && c <= 'Z') {
            c = c + ('a' - 'A');
        }
    }
    return result;
}

static bool startsWith(const std::string& str, const std::string& prefix) {
    if (str.length() < prefix.length()) return false;
    return toLower(str.substr(0, prefix.length())) == toLower(prefix);
}

static bool isImportantProcess(const std::string& name) {
    for (int i = 0; IMPORTANT_PROCESSES[i] != nullptr; i++) {
        if (toLower(name) == toLower(IMPORTANT_PROCESSES[i])) {
            return true;
        }
    }
    return false;
}

static bool isJunkProcess(const std::string& name) {
    std::string lowerName = toLower(name);
    return lowerName == "svchost" || lowerName == "powershell" ||
        lowerName == "conhost" || lowerName == "idle" ||
        lowerName == "system" || lowerName == "registry" ||
        lowerName == "taskkill" || lowerName == "wmi" ||
        lowerName == "dllhost" || lowerName == "rundll32" ||
        lowerName == "spoolsv" || lowerName == "services" ||
        lowerName == "lsass" || lowerName == "wininit" ||
        lowerName == "winlogon" || lowerName == "csrss";
}

static std::string escapeJsonString(const std::string& s) {
    std::string result;
    result.reserve(s.length() + 10);

    for (char c : s) {
        if (c == '"') result += "\\\"";
        else if (c == '\\') result += "\\\\";
        else if (c == '\n') result += "\\n";
        else if (c == '\r') result += "\\r";
        else if (c == '\t') result += "\\t";
        else if (c < 32) {
            char buf[8];
            snprintf(buf, sizeof(buf), "\\u%04x", (unsigned char)c);
            result += buf;
        }
        else result += c;
    }
    return result;
}

static std::string formatDouble(double value, int precision = 1) {
    char buffer[32];
    snprintf(buffer, sizeof(buffer), "%.*f", precision, value);
    return std::string(buffer);
}

// ==================== CPU USAGE ====================

static int getCpuTotalUsage() {
    if (!g_initialized) return 0;

    PDH_FMT_COUNTERVALUE counterVal;
    PdhCollectQueryData(cpuQuery);
    if (PdhGetFormattedCounterValue(cpuTotal, PDH_FMT_DOUBLE, NULL, &counterVal) == ERROR_SUCCESS) {
        int val = (int)counterVal.doubleValue;
        return (val < 0) ? 0 : (val > 100 ? 100 : val);
    }
    return 0;
}

// ==================== PUBLIC API ====================

void TaskManager::init() {
    if (g_initialized) return;

    PdhOpenQuery(NULL, NULL, &cpuQuery);
    PdhAddCounter(cpuQuery, L"\\Processor(_Total)\\% Processor Time", NULL, &cpuTotal);
    PdhCollectQueryData(cpuQuery);

    g_cpuSamples.resize(65536);
    g_initialized = true;
}

void TaskManager::cleanup() {
    if (cpuQuery) {
        PdhCloseQuery(cpuQuery);
        cpuQuery = NULL;
    }
    g_cpuSamples.clear();
    g_initialized = false;
}

std::string TaskManager::getOneTimeSnapshot() {
    if (!g_initialized) init();

    ULONGLONG now = GetTickCount64();
    static ULONGLONG lastTick = 0;
    bool isFirstRun = (lastTick == 0);

    int cpuTotal = getCpuTotalUsage();

    MEMORYSTATUSEX memInfo;
    memInfo.dwLength = sizeof(memInfo);
    GlobalMemoryStatusEx(&memInfo);

    ULONGLONG totalPhys = memInfo.ullTotalPhys;
    ULONGLONG usedPhys = totalPhys - memInfo.ullAvailPhys;

    int ramUsedMB = (int)(usedPhys / 1048576);
    int ramTotalMB = (int)(totalPhys / 1048576);
    int ramPercent = (int)((usedPhys * 100) / totalPhys);

    g_lastTotalRamKB = totalPhys / 1024;

    static SYSTEM_INFO si;
    static bool siInitialized = false;
    if (!siInitialized) {
        GetSystemInfo(&si);
        siInitialized = true;
    }

    struct ProcessData {
        int pid;
        double cpu;
        double ramMB;
        std::string name;
        bool isImportant;
    };
    std::vector<ProcessData> processList;
    processList.reserve(500);

    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap != INVALID_HANDLE_VALUE) {
        PROCESSENTRY32W pe;
        pe.dwSize = sizeof(pe);

        if (Process32FirstW(snap, &pe)) {
            do {
                int pid = pe.th32ProcessID;
                if (pid == 0 || pid == 4 || pid == (int)GetCurrentProcessId()) continue;

                char exeName[260] = { 0 };
                WideCharToMultiByte(CP_UTF8, 0, pe.szExeFile, -1, exeName, 260, NULL, NULL);
                std::string processName(exeName);

                if (isJunkProcess(processName)) continue;

                HANDLE hProcess = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);
                if (!hProcess) continue;

                double cpu = 0.0;
                double ramMB = 0.0;

                // Get CPU time
                FILETIME createTime, exitTime, kernelTime, userTime;
                if (GetProcessTimes(hProcess, &createTime, &exitTime, &kernelTime, &userTime)) {
                    ULONGLONG cpuTime = ((ULONGLONG)kernelTime.dwHighDateTime << 32) | kernelTime.dwLowDateTime;
                    cpuTime += ((ULONGLONG)userTime.dwHighDateTime << 32) | userTime.dwLowDateTime;

                    if (pid >= 0 && pid < 65536) {
                        CpuSample& sample = g_cpuSamples[pid];
                        if (!isFirstRun && sample.lastTime > 0 && now > sample.lastTime) {
                            ULONGLONG deltaCpu = cpuTime - sample.lastCpu;
                            ULONGLONG deltaTime = (now - sample.lastTime) * 10000;
                            if (deltaTime > 0) {
                                if (deltaCpu > deltaTime) deltaCpu = deltaTime;
                                cpu = (deltaCpu * 100.0) / (deltaTime * si.dwNumberOfProcessors);
                                if (cpu < 0) cpu = 0;
                                if (cpu > 100) cpu = 100;
                                cpu = round(cpu * 10.0) / 10.0;
                            }
                        }
                        sample.lastTime = now;
                        sample.lastCpu = cpuTime;
                    }
                }

                // Get RAM in MB
                PROCESS_MEMORY_COUNTERS pmc;
                pmc.cb = sizeof(pmc);
                if (GetProcessMemoryInfo(hProcess, &pmc, sizeof(pmc))) {
                    ramMB = pmc.WorkingSetSize / 1048576.0;
                    ramMB = round(ramMB * 10.0) / 10.0;
                }

                CloseHandle(hProcess);

                bool isImportant = isImportantProcess(processName);

                bool shouldAdd = false;
                if (isImportant) {
                    shouldAdd = true;
                }
                else if (cpu >= MIN_CPU_PERCENT) {
                    shouldAdd = true;
                }
                else if (ramMB >= MIN_RAM_MB) {
                    shouldAdd = true;
                }

                if (shouldAdd) {
                    processList.push_back({ pid, cpu, ramMB, processName, isImportant });
                }

            } while (Process32NextW(snap, &pe));
        }
        CloseHandle(snap);
    }

    lastTick = now;

    // Sort processes
    std::sort(processList.begin(), processList.end(),
        [](const ProcessData& a, const ProcessData& b) {
            if (a.isImportant != b.isImportant) {
                return a.isImportant > b.isImportant;
            }
            if (std::abs(a.cpu - b.cpu) > 0.01) {
                return a.cpu > b.cpu;
            }
            if (std::abs(a.ramMB - b.ramMB) > 0.01) {
                return a.ramMB > b.ramMB;
            }
            return a.name < b.name;
        });

    size_t totalProcesses = processList.size();
    size_t processesToSend = std::min<size_t>(totalProcesses, (size_t)MAX_PROCESSES);

    // Build JSON
    std::string json = "{\"cpu_total\":" + std::to_string(cpuTotal) +
        ",\"ram_used_mb\":" + std::to_string(ramUsedMB) +
        ",\"ram_total_mb\":" + std::to_string(ramTotalMB) +
        ",\"ram_percent\":" + std::to_string(ramPercent) +
        ",\"processes\":[";

    for (size_t i = 0; i < processesToSend; ++i) {
        if (i > 0) json += ",";

        json += "{\"pid\":" + std::to_string(processList[i].pid) +
            ",\"cpu\":" + formatDouble(processList[i].cpu, 1) +
            ",\"ram_mb\":" + formatDouble(processList[i].ramMB, 1) +
            ",\"name\":\"" + escapeJsonString(processList[i].name) + "\"}";
    }

    json += "]}";

    return json;
}

bool TaskManager::killProcess(int pid) {
    if (pid <= 4 || pid == (int)GetCurrentProcessId()) return false;

    HANDLE hProcess = OpenProcess(PROCESS_TERMINATE, FALSE, pid);
    if (!hProcess) return false;

    BOOL result = TerminateProcess(hProcess, 0);
    CloseHandle(hProcess);

    return result != 0;
}