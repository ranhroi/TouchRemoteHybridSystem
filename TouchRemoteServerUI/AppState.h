#pragma once

#include <msclr/marshal_cppstd.h>
#include <Windows.h>
#include <shellapi.h>

using namespace System;
using namespace System::IO;
using namespace System::Text;
using namespace Microsoft::Win32;
using namespace System::Windows::Forms;

namespace TouchRemoteServerUI {

    public ref class AppState
    {
    private:
        static AppState^ m_instance = nullptr;

        // Settings
        int m_tcpPort;
        int m_udpPort;
        bool m_bluetoothEnabled;
        bool m_autoRunWithWindows;
        bool m_autoStartServer;
        bool m_minimizeToTray;
        bool m_showNotifications;
        bool m_needPassword;
        String^ m_password;
        String^ m_btUuid;

        // Runtime state (không lưu vào file)
        bool m_serverRunning;
        int m_connectionCount;

        AppState()
        {
            LoadSettings();
            m_serverRunning = false;
            m_connectionCount = 0;
        }

    public:
        static AppState^ Instance()
        {
            if (m_instance == nullptr)
            {
                m_instance = gcnew AppState();
            }
            return m_instance;
        }

        // Settings Properties
        property int TCPPort
        {
            int get() { return m_tcpPort; }
            void set(int value)
            {
                if (value > 0 && value < 65535 && m_tcpPort != value)
                {
                    m_tcpPort = value;
                    SaveSettings();
                }
            }
        }

        property int UDPPort
        {
            int get() { return m_udpPort; }
            void set(int value)
            {
                if (value > 0 && value < 65535 && m_udpPort != value)
                {
                    m_udpPort = value;
                    SaveSettings();
                }
            }
        }

        property bool BluetoothEnabled
        {
            bool get() { return m_bluetoothEnabled; }
            void set(bool value)
            {
                if (m_bluetoothEnabled != value)
                {
                    m_bluetoothEnabled = value;
                    SaveSettings();
                }
            }
        }

        property bool AutoRunWithWindows
        {
            bool get() { return m_autoRunWithWindows; }
            void set(bool value)
            {
                if (m_autoRunWithWindows != value)
                {
                    m_autoRunWithWindows = value;
                    SetAutoRunRegistry(value);
                    SaveSettings();
                }
            }
        }

        property bool AutoStartServer
        {
            bool get() { return m_autoStartServer; }
            void set(bool value)
            {
                if (m_autoStartServer != value)
                {
                    m_autoStartServer = value;
                    SaveSettings();
                }
            }
        }

        property bool MinimizeToTray
        {
            bool get() { return m_minimizeToTray; }
            void set(bool value)
            {
                if (m_minimizeToTray != value)
                {
                    m_minimizeToTray = value;
                    SaveSettings();
                }
            }
        }

        property bool ShowNotifications
        {
            bool get() { return m_showNotifications; }
            void set(bool value)
            {
                if (m_showNotifications != value)
                {
                    m_showNotifications = value;
                    SaveSettings();
                }
            }
        }

        property bool NeedPassword
        {
            bool get() { return m_needPassword; }
            void set(bool value)
            {
                if (m_needPassword != value)
                {
                    m_needPassword = value;
                    SaveSettings();
                }
            }
        }

        property String^ Password
        {
            String^ get() { return m_password; }
            void set(String^ value)
            {
                if (m_password != value)
                {
                    m_password = value;
                    SaveSettings();
                }
            }
        }


        property String^ BluetoothUUID
        {
            String^ get() { return m_btUuid; }
            void set(String^ value)
            {
                if (m_btUuid != value)
                {
                    m_btUuid = value;
                    SaveSettings();
                }
            }
        }

        // Runtime State Properties (Không lưu vào file)
        property bool ServerRunning
        {
            bool get() { return m_serverRunning; }
            void set(bool value) { m_serverRunning = value; }
        }

        property int ConnectionCount
        {
            int get() { return m_connectionCount; }
            void set(int value) { m_connectionCount = value; }
        }

        void LoadSettings()
        {
            String^ configDir = Path::Combine(Environment::GetFolderPath(Environment::SpecialFolder::LocalApplicationData),
                L"TouchRemoteServer");
            String^ configPath = Path::Combine(configDir, L"config.dat");

            // Default values
            m_tcpPort = 2026;
            m_udpPort = 2025;
            m_bluetoothEnabled = true;
            m_autoRunWithWindows = false;
            m_autoStartServer = false;
            m_minimizeToTray = true;
            m_showNotifications = true;
            m_needPassword = false;
            m_password = L"";
            m_btUuid = L"c05efa2b-5e9f-4a39-9705-72ccf47d2eb8";

            if (File::Exists(configPath))
            {
                try
                {
                    array<String^>^ lines = File::ReadAllLines(configPath);
                    for each (String ^ line in lines)
                    {
                        array<String^>^ parts = line->Split('=');
                        if (parts->Length == 2)
                        {
                            String^ key = parts[0];
                            String^ value = parts[1];

                            if (key == L"TCPPort") int::TryParse(value, m_tcpPort);
                            else if (key == L"UDPPort") int::TryParse(value, m_udpPort);
                            else if (key == L"BluetoothEnabled") Boolean::TryParse(value, m_bluetoothEnabled);
                            else if (key == L"AutoRunWithWindows") Boolean::TryParse(value, m_autoRunWithWindows);
                            else if (key == L"AutoStartServer") Boolean::TryParse(value, m_autoStartServer);
                            else if (key == L"MinimizeToTray") Boolean::TryParse(value, m_minimizeToTray);
                            else if (key == L"ShowNotifications") Boolean::TryParse(value, m_showNotifications);
                            else if (key == L"NeedPassword") Boolean::TryParse(value, m_needPassword);
                            else if (key == L"Password") m_password = value;
                            else if (key == L"BluetoothUUID") m_btUuid = value;
                        }
                    }
                }
                catch (Exception^) { /* Use defaults */ }
            }
        }

        void SaveSettings()
        {
            String^ configDir = Path::Combine(Environment::GetFolderPath(Environment::SpecialFolder::LocalApplicationData),
                L"TouchRemoteServer");

            if (!Directory::Exists(configDir))
            {
                Directory::CreateDirectory(configDir);
            }

            String^ configPath = Path::Combine(configDir, L"config.dat");

            StreamWriter^ writer = nullptr;
            try
            {
                writer = gcnew StreamWriter(configPath, false, Encoding::UTF8);
                writer->WriteLine(String::Format(L"TCPPort={0}", m_tcpPort));
                writer->WriteLine(String::Format(L"UDPPort={0}", m_udpPort));
                writer->WriteLine(String::Format(L"BluetoothEnabled={0}", m_bluetoothEnabled));
                writer->WriteLine(String::Format(L"AutoRunWithWindows={0}", m_autoRunWithWindows));
                writer->WriteLine(String::Format(L"AutoStartServer={0}", m_autoStartServer));
                writer->WriteLine(String::Format(L"MinimizeToTray={0}", m_minimizeToTray));
                writer->WriteLine(String::Format(L"ShowNotifications={0}", m_showNotifications));
                writer->WriteLine(String::Format(L"NeedPassword={0}", m_needPassword));
                writer->WriteLine(String::Format(L"Password={0}", m_password));
                writer->WriteLine(String::Format(L"BluetoothUUID={0}", m_btUuid));
            }
            finally
            {
                if (writer != nullptr)
                {
                    writer->Close();
                    delete writer;
                }
            }
        }

        void SetAutoRunRegistry(bool enable)
        {
            try
            {
                RegistryKey^ key = Registry::CurrentUser->OpenSubKey(L"Software\\Microsoft\\Windows\\CurrentVersion\\Run", true);
                if (key != nullptr)
                {
                    if (enable)
                    {
                        key->SetValue(L"TouchRemoteServer", Application::ExecutablePath);
                    }
                    else
                    {
                        if (key->GetValue(L"TouchRemoteServer") != nullptr)
                        {
                            key->DeleteValue(L"TouchRemoteServer");
                        }
                    }
                    key->Close();
                }
            }
            catch (Exception^) { /* Ignore registry errors */ }
        }
    };
}