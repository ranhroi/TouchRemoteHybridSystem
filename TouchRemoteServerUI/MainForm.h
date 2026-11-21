#pragma once
#include <msclr/marshal_cppstd.h>
#include "hybridsocket.h"
#include "AppState.h"
#include "TrayManager.h"   

namespace TouchRemoteServerUI {

    using namespace System;
    using namespace System::IO;
    using namespace System::ComponentModel;
    using namespace System::Collections;
    using namespace System::Windows::Forms;
    using namespace System::Data;
    using namespace System::Drawing;
    using namespace System::Threading;
    using namespace System::Runtime::InteropServices;

    /// <summary>
    /// Main Form for Touch Remote Server
    /// </summary>
    public ref class MainForm : public System::Windows::Forms::Form
    {
    public:
        MainForm(void)
        {
            InitializeComponent();

            try
            {
                HINSTANCE hInstance = GetModuleHandle(nullptr);
                HICON hIcon = LoadIcon(hInstance, MAKEINTRESOURCE(IDI_ICON1));
                if (hIcon != nullptr)
                {
                     System::Drawing::Icon^ icon = System::Drawing::Icon::FromHandle(IntPtr(hIcon));
                    this->Icon = gcnew  System::Drawing::Icon(icon, 32, 32); // Resize cho main form
                }
            }
            catch (Exception^) { /* Fallback */ }

            isExiting = false;
            minimizeToTray = true;

            SetupUI();
            SetupConnections();

            trayManager = gcnew TrayManager();
            ConnectTrayEvents();

            LoadSettings();
            StartServerIfAutoStart();

            this->Show();
            this->WindowState = FormWindowState::Normal;
            this->Activate();
        }

    protected:
        ~MainForm()
        {
            if (!isExiting)
            {
                isExiting = true;
                if (RemoteServer_IsRunning())
                {
                    RemoteServer_Stop();
                }
                RemoteServer_Cleanup();
            }

            if (mainTimer != nullptr)
            {
                mainTimer->Stop();
                delete mainTimer;
                mainTimer = nullptr;
            }

            if (trayManager != nullptr)
            {
                delete trayManager;
                trayManager = nullptr;
            }

            if (components)
            {
                delete components;
            }
        }

    protected:
        virtual void WndProc(Message% m) override
        {
            __super::WndProc(m);
        }

        // ===== XỬ LÝ ĐÓNG FORM =====
        virtual void OnFormClosing(FormClosingEventArgs^ e) override
        {
            if (!isExiting && minimizeToTray && e->CloseReason == CloseReason::UserClosing)
            {
                e->Cancel = true;
                this->Hide();

                if (trayManager != nullptr)
                {
                    trayManager->ShowMessage(L"Touch Remote Server",
                        L"Running in system tray.\nDouble-click icon to restore.",
                        ToolTipIcon::Info, 1000);
                }
            }
            else
            {
                isExiting = true;
                System::Windows::Forms::Form::OnFormClosing(e);
            }
        }

    private:
        System::ComponentModel::IContainer^ components;

        // ===== TRAY MANAGER =====
        TrayManager^ trayManager;

        // ===== Controls =====
        // Buttons
        System::Windows::Forms::Button^ btnActionServer;
        System::Windows::Forms::Button^ btnClearLog;
        System::Windows::Forms::Button^ btnNeedPassword;
        System::Windows::Forms::Button^ btnEnableBluetooth;
        System::Windows::Forms::Button^ btnEnableWiFi;

        // CheckBoxes
        System::Windows::Forms::CheckBox^ chkAutoRunWithWindows;
        System::Windows::Forms::CheckBox^ chkAutoStartServer;

        // Labels hiển thị thông tin
        System::Windows::Forms::Label^ lblLocalIP;
        System::Windows::Forms::Label^ lblTCPPort;
        System::Windows::Forms::Label^ lblUDPPort;
        System::Windows::Forms::Label^ lblPass;
        System::Windows::Forms::Label^ lblBluetoothUUID;

        // Labels tiêu đề
        System::Windows::Forms::Label^ lblVersion;
        System::Windows::Forms::Label^ lblIP;
        System::Windows::Forms::Label^ lblTCP;
        System::Windows::Forms::Label^ lblUDP;
        System::Windows::Forms::Label^ lblPassword;
        System::Windows::Forms::Label^ lblBluetooth;
        System::Windows::Forms::Label^ lblStatus;

        // ===== Connection Statistics Controls =====
        System::Windows::Forms::GroupBox^ grpStatistics;
        System::Windows::Forms::Label^ lblConnTotal;
        System::Windows::Forms::Label^ lblConnTCP;
        System::Windows::Forms::Label^ lblConnBT;

        // Log
        System::Windows::Forms::RichTextBox^ txtLog;

        // ===== CHỈ 1 TIMER DUY NHẤT =====
        System::Windows::Forms::Timer^ mainTimer;

        System::Windows::Forms::ToolTip^ toolTip;

        // State
        bool isServerRunning;
        int currentConnections;
        int tempTCPPort;
        int tempUDPPort;
        int tempUSBPort;
        String^ tempUSBCom;
        bool isUpdatingUI;
        bool minimizeToTray;
        bool isExiting;

        // Delegate
        delegate void LogDelegate(String^ message, int level);

#pragma region Windows Form Designer generated code
        void InitializeComponent(void)
        {
            this->components = (gcnew System::ComponentModel::Container());
            System::ComponentModel::ComponentResourceManager^ resources = (gcnew System::ComponentModel::ComponentResourceManager(MainForm::typeid));
            this->btnActionServer = (gcnew System::Windows::Forms::Button());
            this->btnClearLog = (gcnew System::Windows::Forms::Button());
            this->btnNeedPassword = (gcnew System::Windows::Forms::Button());
            this->btnEnableBluetooth = (gcnew System::Windows::Forms::Button());
            this->btnEnableWiFi = (gcnew System::Windows::Forms::Button());
            this->chkAutoRunWithWindows = (gcnew System::Windows::Forms::CheckBox());
            this->chkAutoStartServer = (gcnew System::Windows::Forms::CheckBox());
            this->txtLog = (gcnew System::Windows::Forms::RichTextBox());
            this->lblVersion = (gcnew System::Windows::Forms::Label());
            this->lblIP = (gcnew System::Windows::Forms::Label());
            this->lblTCP = (gcnew System::Windows::Forms::Label());
            this->lblUDP = (gcnew System::Windows::Forms::Label());
            this->lblPassword = (gcnew System::Windows::Forms::Label());
            this->lblBluetooth = (gcnew System::Windows::Forms::Label());
            this->lblStatus = (gcnew System::Windows::Forms::Label());
            this->mainTimer = (gcnew System::Windows::Forms::Timer(this->components));
            this->lblLocalIP = (gcnew System::Windows::Forms::Label());
            this->lblTCPPort = (gcnew System::Windows::Forms::Label());
            this->lblUDPPort = (gcnew System::Windows::Forms::Label());
            this->lblPass = (gcnew System::Windows::Forms::Label());
            this->lblBluetoothUUID = (gcnew System::Windows::Forms::Label());
            this->grpStatistics = (gcnew System::Windows::Forms::GroupBox());
            this->lblConnTotal = (gcnew System::Windows::Forms::Label());
            this->lblConnTCP = (gcnew System::Windows::Forms::Label());
            this->lblConnBT = (gcnew System::Windows::Forms::Label());
            this->grpStatistics->SuspendLayout();
            this->SuspendLayout();
            // 
            // btnActionServer
            // 
            this->btnActionServer->BackColor = System::Drawing::Color::LightGreen;
            this->btnActionServer->Cursor = System::Windows::Forms::Cursors::Hand;
            this->btnActionServer->FlatStyle = System::Windows::Forms::FlatStyle::Popup;
            this->btnActionServer->Location = System::Drawing::Point(434, 47);
            this->btnActionServer->Name = L"btnActionServer";
            this->btnActionServer->Size = System::Drawing::Size(90, 25);
            this->btnActionServer->TabIndex = 6;
            this->btnActionServer->Text = L"Start";
            this->btnActionServer->UseVisualStyleBackColor = false;
            // 
            // btnClearLog
            // 
            this->btnClearLog->Cursor = System::Windows::Forms::Cursors::Hand;
            this->btnClearLog->FlatStyle = System::Windows::Forms::FlatStyle::Popup;
            this->btnClearLog->Location = System::Drawing::Point(434, 150);
            this->btnClearLog->Name = L"btnClearLog";
            this->btnClearLog->Size = System::Drawing::Size(90, 25);
            this->btnClearLog->TabIndex = 20;
            this->btnClearLog->Text = L"Clear Log";
            // 
            // btnNeedPassword
            // 
            this->btnNeedPassword->BackColor = System::Drawing::Color::LightGray;
            this->btnNeedPassword->Cursor = System::Windows::Forms::Cursors::Hand;
            this->btnNeedPassword->FlatStyle = System::Windows::Forms::FlatStyle::Popup;
            this->btnNeedPassword->Location = System::Drawing::Point(434, 84);
            this->btnNeedPassword->Name = L"btnNeedPassword";
            this->btnNeedPassword->Size = System::Drawing::Size(90, 25);
            this->btnNeedPassword->TabIndex = 14;
            this->btnNeedPassword->Text = L"PASS OFF";
            this->btnNeedPassword->UseVisualStyleBackColor = false;
            // 
            // btnEnableBluetooth
            // 
            this->btnEnableBluetooth->BackColor = System::Drawing::Color::LightGray;
            this->btnEnableBluetooth->Cursor = System::Windows::Forms::Cursors::Hand;
            this->btnEnableBluetooth->FlatStyle = System::Windows::Forms::FlatStyle::Popup;
            this->btnEnableBluetooth->Location = System::Drawing::Point(434, 117);
            this->btnEnableBluetooth->Name = L"btnEnableBluetooth";
            this->btnEnableBluetooth->Size = System::Drawing::Size(90, 25);
            this->btnEnableBluetooth->TabIndex = 17;
            this->btnEnableBluetooth->Text = L"BT OFF";
            this->btnEnableBluetooth->UseVisualStyleBackColor = false;
            // 
            // btnEnableWiFi
            // 
            this->btnEnableWiFi->BackColor = System::Drawing::Color::LightGray;
            this->btnEnableWiFi->Cursor = System::Windows::Forms::Cursors::Hand;
            this->btnEnableWiFi->FlatStyle = System::Windows::Forms::FlatStyle::Popup;
            this->btnEnableWiFi->Location = System::Drawing::Point(434, 10);
            this->btnEnableWiFi->Name = L"btnEnableWiFi";
            this->btnEnableWiFi->Size = System::Drawing::Size(90, 25);
            this->btnEnableWiFi->TabIndex = 2;
            this->btnEnableWiFi->Text = L"WiFi OFF";
            this->btnEnableWiFi->UseVisualStyleBackColor = false;
            // 
            // chkAutoRunWithWindows
            // 
            this->chkAutoRunWithWindows->AutoSize = true;
            this->chkAutoRunWithWindows->Location = System::Drawing::Point(161, 154);
            this->chkAutoRunWithWindows->Name = L"chkAutoRunWithWindows";
            this->chkAutoRunWithWindows->Size = System::Drawing::Size(135, 17);
            this->chkAutoRunWithWindows->TabIndex = 18;
            this->chkAutoRunWithWindows->Text = L"Auto run with Windows";
            // 
            // chkAutoStartServer
            // 
            this->chkAutoStartServer->AutoSize = true;
            this->chkAutoStartServer->Location = System::Drawing::Point(316, 154);
            this->chkAutoStartServer->Name = L"chkAutoStartServer";
            this->chkAutoStartServer->Size = System::Drawing::Size(103, 17);
            this->chkAutoStartServer->TabIndex = 19;
            this->chkAutoStartServer->Text = L"Auto start server";
            // 
            // txtLog
            // 
            this->txtLog->Font = (gcnew System::Drawing::Font(L"Consolas", 9));
            this->txtLog->HideSelection = false;
            this->txtLog->Location = System::Drawing::Point(12, 219);
            this->txtLog->Name = L"txtLog";
            this->txtLog->ReadOnly = true;
            this->txtLog->Size = System::Drawing::Size(578, 142);
            this->txtLog->TabIndex = 21;
            this->txtLog->Text = L"";
            // 
            // lblVersion
            // 
            this->lblVersion->AutoSize = true;
            this->lblVersion->Location = System::Drawing::Point(228, 378);
            this->lblVersion->Name = L"lblVersion";
            this->lblVersion->Size = System::Drawing::Size(168, 13);
            this->lblVersion->TabIndex = 23;
            this->lblVersion->Text = L"Version 1.0 - TouchRemoteServer";
            // 
            // lblIP
            // 
            this->lblIP->AutoSize = true;
            this->lblIP->Cursor = System::Windows::Forms::Cursors::Hand;
            this->lblIP->Location = System::Drawing::Point(73, 16);
            this->lblIP->Name = L"lblIP";
            this->lblIP->Size = System::Drawing::Size(62, 13);
            this->lblIP->TabIndex = 0;
            this->lblIP->Text = L"ADDRESS:";
            // 
            // lblTCP
            // 
            this->lblTCP->AutoSize = true;
            this->lblTCP->Cursor = System::Windows::Forms::Cursors::Hand;
            this->lblTCP->Location = System::Drawing::Point(73, 53);
            this->lblTCP->Name = L"lblTCP";
            this->lblTCP->Size = System::Drawing::Size(63, 13);
            this->lblTCP->TabIndex = 3;
            this->lblTCP->Text = L"TCP - UDP:";
            // 
            // lblUDP
            // 
            this->lblUDP->Location = System::Drawing::Point(0, 0);
            this->lblUDP->Name = L"lblUDP";
            this->lblUDP->Size = System::Drawing::Size(100, 23);
            this->lblUDP->TabIndex = 0;
            // 
            // lblPassword
            // 
            this->lblPassword->BorderStyle = System::Windows::Forms::BorderStyle::Fixed3D;
            this->lblPassword->Location = System::Drawing::Point(161, 84);
            this->lblPassword->Name = L"lblPassword";
            this->lblPassword->Size = System::Drawing::Size(258, 23);
            this->lblPassword->TabIndex = 0;
            this->lblPassword->TextAlign = System::Drawing::ContentAlignment::MiddleCenter;
            // 
            // lblBluetooth
            // 
            this->lblBluetooth->AutoSize = true;
            this->lblBluetooth->Cursor = System::Windows::Forms::Cursors::Hand;
            this->lblBluetooth->Location = System::Drawing::Point(73, 123);
            this->lblBluetooth->Name = L"lblBluetooth";
            this->lblBluetooth->Size = System::Drawing::Size(54, 13);
            this->lblBluetooth->TabIndex = 15;
            this->lblBluetooth->Text = L"BT UUID:";
            // 
            // lblStatus
            // 
            this->lblStatus->AutoSize = true;
            this->lblStatus->ForeColor = System::Drawing::Color::Gray;
            this->lblStatus->Location = System::Drawing::Point(67, 156);
            this->lblStatus->Name = L"lblStatus";
            this->lblStatus->Size = System::Drawing::Size(60, 13);
            this->lblStatus->TabIndex = 22;
            this->lblStatus->Text = L"Status: Idle";
            // 
            // mainTimer
            // 
            this->mainTimer->Interval = 500;
            // 
            // lblLocalIP
            // 
            this->lblLocalIP->BackColor = System::Drawing::Color::WhiteSmoke;
            this->lblLocalIP->BorderStyle = System::Windows::Forms::BorderStyle::Fixed3D;
            this->lblLocalIP->Cursor = System::Windows::Forms::Cursors::Hand;
            this->lblLocalIP->Font = (gcnew System::Drawing::Font(L"Microsoft Sans Serif", 9));
            this->lblLocalIP->Location = System::Drawing::Point(161, 10);
            this->lblLocalIP->Name = L"lblLocalIP";
            this->lblLocalIP->Size = System::Drawing::Size(258, 25);
            this->lblLocalIP->TabIndex = 1;
            this->lblLocalIP->TextAlign = System::Drawing::ContentAlignment::MiddleCenter;
            // 
            // lblTCPPort
            // 
            this->lblTCPPort->BorderStyle = System::Windows::Forms::BorderStyle::Fixed3D;
            this->lblTCPPort->Cursor = System::Windows::Forms::Cursors::Hand;
            this->lblTCPPort->Font = (gcnew System::Drawing::Font(L"Microsoft Sans Serif", 9));
            this->lblTCPPort->Location = System::Drawing::Point(161, 47);
            this->lblTCPPort->Name = L"lblTCPPort";
            this->lblTCPPort->Size = System::Drawing::Size(129, 25);
            this->lblTCPPort->TabIndex = 4;
            this->lblTCPPort->TextAlign = System::Drawing::ContentAlignment::MiddleCenter;
            // 
            // lblUDPPort
            // 
            this->lblUDPPort->BorderStyle = System::Windows::Forms::BorderStyle::Fixed3D;
            this->lblUDPPort->Cursor = System::Windows::Forms::Cursors::Hand;
            this->lblUDPPort->Font = (gcnew System::Drawing::Font(L"Microsoft Sans Serif", 9));
            this->lblUDPPort->Location = System::Drawing::Point(296, 47);
            this->lblUDPPort->Name = L"lblUDPPort";
            this->lblUDPPort->Size = System::Drawing::Size(123, 25);
            this->lblUDPPort->TabIndex = 5;
            this->lblUDPPort->TextAlign = System::Drawing::ContentAlignment::MiddleCenter;
            // 
            // lblPass
            // 
            this->lblPass->AutoSize = true;
            this->lblPass->Cursor = System::Windows::Forms::Cursors::Hand;
            this->lblPass->Font = (gcnew System::Drawing::Font(L"Microsoft Sans Serif", 9));
            this->lblPass->Location = System::Drawing::Point(73, 90);
            this->lblPass->Name = L"lblPass";
            this->lblPass->Size = System::Drawing::Size(41, 15);
            this->lblPass->TabIndex = 12;
            this->lblPass->Text = L"PASS:";
            // 
            // lblBluetoothUUID
            // 
            this->lblBluetoothUUID->BackColor = System::Drawing::Color::WhiteSmoke;
            this->lblBluetoothUUID->BorderStyle = System::Windows::Forms::BorderStyle::Fixed3D;
            this->lblBluetoothUUID->Cursor = System::Windows::Forms::Cursors::Hand;
            this->lblBluetoothUUID->Font = (gcnew System::Drawing::Font(L"Microsoft Sans Serif", 9));
            this->lblBluetoothUUID->Location = System::Drawing::Point(161, 117);
            this->lblBluetoothUUID->Name = L"lblBluetoothUUID";
            this->lblBluetoothUUID->Size = System::Drawing::Size(258, 25);
            this->lblBluetoothUUID->TabIndex = 16;
            this->lblBluetoothUUID->TextAlign = System::Drawing::ContentAlignment::MiddleCenter;
            // 
            // grpStatistics
            // 
            this->grpStatistics->Controls->Add(this->lblConnTotal);
            this->grpStatistics->Controls->Add(this->lblConnTCP);
            this->grpStatistics->Controls->Add(this->lblConnBT);
            this->grpStatistics->Font = (gcnew System::Drawing::Font(L"Segoe UI", 8, System::Drawing::FontStyle::Bold));
            this->grpStatistics->ForeColor = System::Drawing::Color::FromArgb(static_cast<System::Int32>(static_cast<System::Byte>(50)), static_cast<System::Int32>(static_cast<System::Byte>(50)),
                static_cast<System::Int32>(static_cast<System::Byte>(50)));
            this->grpStatistics->Location = System::Drawing::Point(161, 177);
            this->grpStatistics->Name = L"grpStatistics";
            this->grpStatistics->Size = System::Drawing::Size(258, 36);
            this->grpStatistics->TabIndex = 30;
            this->grpStatistics->TabStop = false;
            // 
            // lblConnTotal
            // 
            this->lblConnTotal->Font = (gcnew System::Drawing::Font(L"Segoe UI", 9, System::Drawing::FontStyle::Bold));
            this->lblConnTotal->ForeColor = System::Drawing::Color::FromArgb(static_cast<System::Int32>(static_cast<System::Byte>(0)), static_cast<System::Int32>(static_cast<System::Byte>(120)),
                static_cast<System::Int32>(static_cast<System::Byte>(215)));
            this->lblConnTotal->Location = System::Drawing::Point(6, 10);
            this->lblConnTotal->Name = L"lblConnTotal";
            this->lblConnTotal->Size = System::Drawing::Size(54, 20);
            this->lblConnTotal->TabIndex = 1;
            this->lblConnTotal->Text = L"Total: 0";
            // 
            // lblConnTCP
            // 
            this->lblConnTCP->Font = (gcnew System::Drawing::Font(L"Microsoft Sans Serif", 9));
            this->lblConnTCP->ForeColor = System::Drawing::Color::FromArgb(static_cast<System::Int32>(static_cast<System::Byte>(0)), static_cast<System::Int32>(static_cast<System::Byte>(150)),
                static_cast<System::Int32>(static_cast<System::Byte>(0)));
            this->lblConnTCP->Location = System::Drawing::Point(100, 10);
            this->lblConnTCP->Name = L"lblConnTCP";
            this->lblConnTCP->Size = System::Drawing::Size(44, 20);
            this->lblConnTCP->TabIndex = 2;
            this->lblConnTCP->Text = L"TCP: 0";
            // 
            // lblConnBT
            // 
            this->lblConnBT->Font = (gcnew System::Drawing::Font(L"Microsoft Sans Serif", 9));
            this->lblConnBT->ForeColor = System::Drawing::Color::FromArgb(static_cast<System::Int32>(static_cast<System::Byte>(0)), static_cast<System::Int32>(static_cast<System::Byte>(0)),
                static_cast<System::Int32>(static_cast<System::Byte>(200)));
            this->lblConnBT->Location = System::Drawing::Point(208, 9);
            this->lblConnBT->Name = L"lblConnBT";
            this->lblConnBT->Size = System::Drawing::Size(40, 20);
            this->lblConnBT->TabIndex = 3;
            this->lblConnBT->Text = L"BT: 0";
            // 
            // MainForm
            // 
            this->ClientSize = System::Drawing::Size(602, 400);
            this->Controls->Add(this->grpStatistics);
            this->Controls->Add(this->lblIP);
            this->Controls->Add(this->lblLocalIP);
            this->Controls->Add(this->btnEnableWiFi);
            this->Controls->Add(this->lblTCP);
            this->Controls->Add(this->lblTCPPort);
            this->Controls->Add(this->lblUDPPort);
            this->Controls->Add(this->btnActionServer);
            this->Controls->Add(this->lblPassword);
            this->Controls->Add(this->lblPass);
            this->Controls->Add(this->btnNeedPassword);
            this->Controls->Add(this->lblBluetooth);
            this->Controls->Add(this->lblBluetoothUUID);
            this->Controls->Add(this->btnEnableBluetooth);
            this->Controls->Add(this->chkAutoRunWithWindows);
            this->Controls->Add(this->chkAutoStartServer);
            this->Controls->Add(this->btnClearLog);
            this->Controls->Add(this->txtLog);
            this->Controls->Add(this->lblStatus);
            this->Controls->Add(this->lblVersion);
            this->FormBorderStyle = System::Windows::Forms::FormBorderStyle::FixedSingle;
            this->Icon = (cli::safe_cast<System::Drawing::Icon^>(resources->GetObject(L"$this.Icon")));
            this->MaximizeBox = false;
            this->Name = L"MainForm";
            this->StartPosition = System::Windows::Forms::FormStartPosition::CenterScreen;
            this->Text = L"Touch Remote Server Control";
            this->grpStatistics->ResumeLayout(false);
            this->ResumeLayout(false);
            this->PerformLayout();

        }
#pragma endregion

        // ===== KẾT NỐI SỰ KIỆN TRAY =====
        void ConnectTrayEvents()
        {
            if (trayManager == nullptr) return;

            // Kết nối các sự kiện từ Tray Manager
            trayManager->AddStartServerHandler(gcnew EventHandler(this, &MainForm::OnTrayStartServer));
            trayManager->AddStopServerHandler(gcnew EventHandler(this, &MainForm::OnTrayStopServer));
            trayManager->AddShowWindowHandler(gcnew EventHandler(this, &MainForm::OnTrayShowWindow));
            trayManager->AddExitHandler(gcnew EventHandler(this, &MainForm::OnTrayExit));
            trayManager->AddAutoStartServerHandler(gcnew EventHandler(this, &MainForm::OnTrayAutoStartClicked));
            trayManager->AddAutoRunWithWindowsHandler(gcnew EventHandler(this, &MainForm::OnTrayAutoRunClicked));
            trayManager->AddDoubleClickHandler(gcnew EventHandler(this, &MainForm::OnTrayDoubleClick));

            // Đồng bộ trạng thái ban đầu
            AppState^ state = AppState::Instance();
            trayManager->UpdateAutoStartServer(state->AutoStartServer);
            trayManager->UpdateAutoRunWithWindows(state->AutoRunWithWindows);
        }

        // ===== CÁC PHƯƠNG THỨC KHÁC =====
        void SetupUI()
        {
            String^ appDir = AppDomain::CurrentDomain->BaseDirectory;
            String^ iconPath = System::IO::Path::Combine(appDir, L"assets\\icon_main.ico");
            try {
                if (System::IO::File::Exists(iconPath)) {
                    this->Icon = gcnew System::Drawing::Icon(iconPath);
                }
            }
            catch (...) {}

            LoadLocalIP();
            txtLog->WordWrap = false;
            txtLog->ScrollBars = RichTextBoxScrollBars::Both;

            isServerRunning = false;
            currentConnections = 0;
            isUpdatingUI = false;
            minimizeToTray = true;

            // Đăng ký sự kiện Click
            lblIP->Click += gcnew EventHandler(this, &MainForm::lblIP_Click);
            lblTCP->Click += gcnew EventHandler(this, &MainForm::lblTCP_Click);
            lblBluetooth->Click += gcnew EventHandler(this, &MainForm::lblBluetooth_Click);
            lblPass->Click += gcnew EventHandler(this, &MainForm::lblPass_Click);
            lblPassword->Click += gcnew EventHandler(this, &MainForm::lblPassword_Click);

            lblTCPPort->Click += gcnew EventHandler(this, &MainForm::lblTCPPort_Click);
            lblTCPPort->Cursor = Cursors::Hand;
            lblUDPPort->Click += gcnew EventHandler(this, &MainForm::lblUDPPort_Click);
            lblUDPPort->Cursor = Cursors::Hand;
            lblBluetoothUUID->Click += gcnew EventHandler(this, &MainForm::lblBluetoothUUID_Click);
            lblBluetoothUUID->Cursor = Cursors::Hand;
            lblLocalIP->Click += gcnew EventHandler(this, &MainForm::lblLocalIP_Click);
            lblLocalIP->Cursor = Cursors::Hand;

            // Tooltips
            toolTip = gcnew ToolTip();
            toolTip->SetToolTip(lblTCPPort, L"Click to edit TCP Port");
            toolTip->SetToolTip(lblUDPPort, L"Click to edit UDP Port");
            toolTip->SetToolTip(lblPassword, L"Click to edit Password (only when Password is enabled)");
            toolTip->SetToolTip(lblBluetoothUUID, L"Click to edit Bluetooth UUID (only when Bluetooth is enabled)");
            toolTip->SetToolTip(grpStatistics, L"Connection statistics: Total, TCP, Bluetooth, USB");
        }

        void SetupConnections()
        {
            btnActionServer->Click += gcnew EventHandler(this, &MainForm::OnActionServerClicked);
            btnClearLog->Click += gcnew EventHandler(this, &MainForm::OnClearLogClicked);
            btnNeedPassword->Click += gcnew EventHandler(this, &MainForm::OnNeedPasswordClicked);
            btnEnableBluetooth->Click += gcnew EventHandler(this, &MainForm::OnEnableBluetoothClicked);
            btnEnableWiFi->Click += gcnew EventHandler(this, &MainForm::OnWiFiClicked);
            chkAutoRunWithWindows->CheckedChanged += gcnew EventHandler(this, &MainForm::OnAutoRunChanged);
            chkAutoStartServer->CheckedChanged += gcnew EventHandler(this, &MainForm::OnAutoStartServerChanged);

            mainTimer->Tick += gcnew EventHandler(this, &MainForm::OnMainTimerTick);
            mainTimer->Start();
        }

        void LoadSettings()
        {
            AppState^ state = AppState::Instance();

            tempTCPPort = state->TCPPort;
            tempUDPPort = state->UDPPort;

            lblTCPPort->Text = tempTCPPort.ToString();
            lblUDPPort->Text = tempUDPPort.ToString();

            chkAutoRunWithWindows->Checked = state->AutoRunWithWindows;
            chkAutoStartServer->Checked = state->AutoStartServer;

            if (!String::IsNullOrEmpty(state->Password))
                lblPassword->Text = state->Password;

            if (!String::IsNullOrEmpty(state->BluetoothUUID))
                lblBluetoothUUID->Text = state->BluetoothUUID;

            UpdatePasswordButton(state->NeedPassword);
            UpdateBluetoothButton(state->BluetoothEnabled);

            btnEnableBluetooth->Tag = state->BluetoothEnabled;

            // Đồng bộ với Tray
            if (trayManager != nullptr)
            {
                trayManager->UpdateAutoStartServer(state->AutoStartServer);
                trayManager->UpdateAutoRunWithWindows(state->AutoRunWithWindows);
            }
        }

        void LoadLocalIP()
        {
            const char* ip = RemoteServer_GetLocalIP();
            String^ ipAddress = ip ? gcnew String(ip) : L"127.0.0.1";
            lblLocalIP->Text = String::IsNullOrEmpty(ipAddress) ? L"127.0.0.1" : ipAddress;
            lblLocalIP->BackColor = ip ? Color::GhostWhite : Color::LightGray;

            bool hasIP = !String::IsNullOrEmpty(ipAddress) && ipAddress != L"127.0.0.1";
            btnEnableWiFi->BackColor = hasIP ? Color::GhostWhite : Color::LightGray;
            btnEnableWiFi->Text = hasIP ? L"WiFi ON" : L"WiFi OFF";
        }

        void StartServerIfAutoStart()
        {
            if (AppState::Instance()->AutoStartServer)
                StartServer();
        }

        void UpdatePasswordButton(bool needPassword)
        {
            if (needPassword)
            {
                btnNeedPassword->Text = L"PASS ON";
                btnNeedPassword->BackColor = Color::GhostWhite;
                lblPassword->BackColor = Color::GhostWhite;
                lblPassword->Cursor = Cursors::Hand;
            }
            else
            {
                btnNeedPassword->Text = L"PASS OFF";
                btnNeedPassword->BackColor = Color::LightGray;
                lblPassword->BackColor = Color::LightGray;
                lblPassword->Cursor = Cursors::Default;
            }
        }

        void UpdateBluetoothButton(bool enabled)
        {
            if (enabled)
            {
                btnEnableBluetooth->Text = L"BT ON";
                btnEnableBluetooth->BackColor = Color::GhostWhite;
                lblBluetoothUUID->BackColor = Color::GhostWhite;
                lblBluetoothUUID->Cursor = Cursors::Hand;
                lblBluetoothUUID->TabStop = true;
            }
            else
            {
                btnEnableBluetooth->Text = L"BT OFF";
                btnEnableBluetooth->BackColor = Color::LightGray;
                lblBluetoothUUID->BackColor = Color::LightGray;
                lblBluetoothUUID->Cursor = Cursors::Default;
                lblBluetoothUUID->TabStop = false;
            }
        }

        void UpdateServerStatusUI(bool running)
        {
            if (running)
            {
                lblStatus->Text = L"Status: Running";
                lblStatus->ForeColor = Color::Green;
                this->Text = L"Touch Remote Server - [RUNNING]";

                btnActionServer->Text = L"Stop";
                btnActionServer->BackColor = Color::LightCoral;
                btnActionServer->ForeColor = Color::White;

                lblTCPPort->BackColor = Color::GhostWhite;
                lblTCPPort->Cursor = Cursors::Hand;
                lblUDPPort->BackColor = Color::GhostWhite;
                lblUDPPort->Cursor = Cursors::Hand;

                // Cập nhật Tray
                if (trayManager != nullptr)
                {
                    trayManager->UpdateServerStatus(true);
                }

                int tcpCount = 0, btCount = 0;
                RemoteServer_GetConnectionStats(&tcpCount, &btCount);
                int total = tcpCount + btCount;

                UpdateConnectionStats(total, tcpCount, btCount);
                UpdateConnectionCountUI(total, tcpCount, btCount);
            }
            else
            {
                lblStatus->Text = L"Status: Stopped";
                lblStatus->ForeColor = Color::Gray;
                this->Text = L"Touch Remote Server - [STOPPED]";

                btnActionServer->Text = L"Start";
                btnActionServer->BackColor = Color::LightGreen;
                btnActionServer->ForeColor = Color::Black;

                lblTCPPort->BackColor = Color::LightGray;
                lblTCPPort->Cursor = Cursors::Default;
                lblUDPPort->BackColor = Color::LightGray;
                lblUDPPort->Cursor = Cursors::Default;

                // Cập nhật Tray
                if (trayManager != nullptr)
                {
                    trayManager->UpdateServerStatus(false);
                    trayManager->UpdateConnectionCount(0, 0, 0);
                }

                ResetConnectionStats();
            }
            AppState::Instance()->ServerRunning = running;
        }

        void UpdateConnectionCountUI(int total, int tcpCount, int btCount)
        {
            currentConnections = total;

            // Cập nhật Tray
            if (trayManager != nullptr)
            {
                trayManager->UpdateConnectionCount(total, tcpCount, btCount);
            }

            AppState::Instance()->ConnectionCount = total;
        }

        void UpdateConnectionStats(int total, int tcpCount, int btCount)
        {
            if (isExiting) return;

            if (lblConnTotal != nullptr)
            {
                lblConnTotal->Text = String::Format(L"Total: {0}", total);
                lblConnTotal->ForeColor = (total > 0) ? Color::Green : Color::Gray;
            }

            if (lblConnTCP != nullptr)
            {
                lblConnTCP->Text = String::Format(L"TCP: {0}", tcpCount);
                lblConnTCP->ForeColor = (tcpCount > 0) ? Color::FromArgb(0, 150, 0) : Color::Gray;
            }

            if (lblConnBT != nullptr)
            {
                lblConnBT->Text = String::Format(L"BT: {0}", btCount);
                lblConnBT->ForeColor = (btCount > 0) ? Color::FromArgb(0, 0, 200) : Color::Gray;
            }


            if (toolTip != nullptr && grpStatistics != nullptr)
            {
                String^ tooltipText = String::Format(L"Total: {0}\nTCP: {1}\nBT: {2}", total, tcpCount, btCount);
                toolTip->SetToolTip(grpStatistics, tooltipText);
            }
        }

        void ResetConnectionStats()
        {
            if (lblConnTotal != nullptr)
            {
                lblConnTotal->Text = L"Total: 0";
                lblConnTotal->ForeColor = Color::Gray;
            }
            if (lblConnTCP != nullptr)
            {
                lblConnTCP->Text = L"TCP: 0";
                lblConnTCP->ForeColor = Color::Gray;
            }
            if (lblConnBT != nullptr)
            {
                lblConnBT->Text = L"BT: 0";
                lblConnBT->ForeColor = Color::Gray;
            }
            if (toolTip != nullptr && grpStatistics != nullptr)
            {
                toolTip->SetToolTip(grpStatistics, L"No connections");
            }
        }

        // ===== Server Control Methods =====
        void StartServer()
        {
            if (RemoteServer_IsRunning())
            {
                AddLog(L"Server is already running!", 0);
                return;
            }

            AppState^ state = AppState::Instance();

            std::string passwordStr = msclr::interop::marshal_as<std::string>(state->Password);
            std::string uuidStr = msclr::interop::marshal_as<std::string>(state->BluetoothUUID);

            RemoteServerConfig config;
            config.tcpPort = state->TCPPort;
            config.udpPort = state->UDPPort;
            config.needPassword = state->NeedPassword;
            config.password = passwordStr.c_str();
            config.enableBluetooth = state->BluetoothEnabled;
            config.bluetoothUuid = uuidStr.c_str();

            AddLog(String::Format(L"Starting server on TCP port {0}, UDP port {1}, Bluetooth: {2}",
                state->TCPPort, state->UDPPort,
                state->BluetoothEnabled ? L"ON" : L"OFF"), 0);

            if (RemoteServer_Start(&config))
            {
                AddLog(L"Server started successfully!", 0);
                isServerRunning = true;
                UpdateServerStatusUI(true);
            }
            else
            {
                AddLog(L"Failed to start server!", 2);
            }
        }

        void StopServer()
        {
            if (!RemoteServer_IsRunning())
            {
                AddLog(L"Server is not running!", 1);
                return;
            }

            AddLog(L"Stopping server...", 0);
            RemoteServer_Stop();
            isServerRunning = false;
            currentConnections = 0;
            UpdateServerStatusUI(false);
            AddLog(L"Server stopped!", 0);
        }

        bool AskForRestart(String^ changeDescription)
        {
            if (!RemoteServer_IsRunning()) return false;

            return (MessageBox::Show(
                String::Format(L"{0} requires server restart to take effect.\n\nDo you want to restart the server now?", changeDescription),
                L"Restart Required",
                MessageBoxButtons::YesNo,
                MessageBoxIcon::Question,
                MessageBoxDefaultButton::Button1) == System::Windows::Forms::DialogResult::Yes);
        }

        void PerformRestart()
        {
            AddLog(L"Restarting server to apply changes...", 0);
            StopServer();
            Thread::Sleep(500);
            StartServer();
        }

        void AddLog(String^ message, int level)
        {
            if (isExiting)
                return;

            if (txtLog == nullptr)
                return;

            if (txtLog->IsDisposed)
                return;

            if (!txtLog->IsHandleCreated)
                return;

            if (txtLog->InvokeRequired)
            {
                LogDelegate^ d = gcnew LogDelegate(this, &MainForm::AddLog);
                txtLog->BeginInvoke(d, gcnew array<Object^>{ message, level });
                return;
            }

            String^ prefix = (level == 2) ? L"[ERROR] " : (level == 1) ? L"[WARN] " : L"[INFO] ";
            txtLog->AppendText(prefix + message + L"\r\n");
            txtLog->ScrollToCaret();
        }

        // ===== Dialog Methods =====
        void ShowEditPortsDialog()
        {
            Form^ dialog = gcnew Form();
            dialog->Text = L"Edit TCP & UDP Ports";
            dialog->Size = Drawing::Size(350, 190);
            dialog->FormBorderStyle = System::Windows::Forms::FormBorderStyle::FixedDialog;
            dialog->MaximizeBox = false;
            dialog->MinimizeBox = false;
            dialog->StartPosition = FormStartPosition::CenterParent;
            dialog->BackColor = Color::White;

            Label^ lblTCP = gcnew Label();
            lblTCP->Text = L"TCP Port:";
            lblTCP->Location = Drawing::Point(15, 18);
            lblTCP->Size = Drawing::Size(70, 20);
            lblTCP->TextAlign = ContentAlignment::MiddleRight;

            TextBox^ txtTCP = gcnew TextBox();
            txtTCP->Location = Drawing::Point(95, 15);
            txtTCP->Size = Drawing::Size(220, 23);
            txtTCP->Text = lblTCPPort->Text;
            txtTCP->TextAlign = HorizontalAlignment::Center;

            Label^ lblUDP = gcnew Label();
            lblUDP->Text = L"UDP Port:";
            lblUDP->Location = Drawing::Point(15, 48);
            lblUDP->Size = Drawing::Size(70, 20);
            lblUDP->TextAlign = ContentAlignment::MiddleRight;

            TextBox^ txtUDP = gcnew TextBox();
            txtUDP->Location = Drawing::Point(95, 45);
            txtUDP->Size = Drawing::Size(220, 23);
            txtUDP->Text = lblUDPPort->Text;
            txtUDP->TextAlign = HorizontalAlignment::Center;

            Label^ lblHint = gcnew Label();
            lblHint->Text = L"Port range: 1 - 65535";
            lblHint->Location = Drawing::Point(95, 73);
            lblHint->Size = Drawing::Size(220, 15);
            lblHint->ForeColor = Color::Gray;
            lblHint->Font = gcnew Drawing::Font(L"Microsoft Sans Serif", 8);

            Panel^ pnlBottom = gcnew Panel();
            pnlBottom->BackColor = Color::FromArgb(240, 240, 240);
            pnlBottom->Dock = DockStyle::Bottom;
            pnlBottom->Height = 48;

            Button^ btnOK = gcnew Button();
            btnOK->Text = L"OK";
            btnOK->Location = Drawing::Point(140, 10);
            btnOK->Size = Drawing::Size(80, 28);
            btnOK->DialogResult = System::Windows::Forms::DialogResult::OK;

            Button^ btnCancel = gcnew Button();
            btnCancel->Text = L"Cancel";
            btnCancel->Location = Drawing::Point(235, 10);
            btnCancel->Size = Drawing::Size(80, 28);
            btnCancel->DialogResult = System::Windows::Forms::DialogResult::Cancel;

            pnlBottom->Controls->Add(btnOK);
            pnlBottom->Controls->Add(btnCancel);

            dialog->Controls->Add(lblTCP);
            dialog->Controls->Add(txtTCP);
            dialog->Controls->Add(lblUDP);
            dialog->Controls->Add(txtUDP);
            dialog->Controls->Add(lblHint);
            dialog->Controls->Add(pnlBottom);

            dialog->AcceptButton = btnOK;
            dialog->CancelButton = btnCancel;

            if (dialog->ShowDialog() == System::Windows::Forms::DialogResult::OK)
            {
                String^ tcpPortStr = txtTCP->Text->Trim();
                if (String::IsNullOrEmpty(tcpPortStr))
                {
                    MessageBox::Show(L"TCP Port cannot be empty!", L"Error", MessageBoxButtons::OK, MessageBoxIcon::Error);
                    return;
                }

                int tcpPort;
                if (!int::TryParse(tcpPortStr, tcpPort) || tcpPort < 1 || tcpPort > 65535)
                {
                    MessageBox::Show(L"Invalid TCP Port!\nPort must be between 1 and 65535.", L"Error", MessageBoxButtons::OK, MessageBoxIcon::Error);
                    return;
                }

                String^ udpPortStr = txtUDP->Text->Trim();
                if (String::IsNullOrEmpty(udpPortStr))
                {
                    MessageBox::Show(L"UDP Port cannot be empty!", L"Error", MessageBoxButtons::OK, MessageBoxIcon::Error);
                    return;
                }

                int udpPort;
                if (!int::TryParse(udpPortStr, udpPort) || udpPort < 1 || udpPort > 65535)
                {
                    MessageBox::Show(L"Invalid UDP Port!\nPort must be between 1 and 65535.", L"Error", MessageBoxButtons::OK, MessageBoxIcon::Error);
                    return;
                }

                if (tcpPort == udpPort)
                {
                    MessageBox::Show(L"TCP Port and UDP Port cannot be the same!", L"Error", MessageBoxButtons::OK, MessageBoxIcon::Error);
                    return;
                }

                if (tempTCPPort != tcpPort || tempUDPPort != udpPort)
                {
                    tempTCPPort = tcpPort;
                    tempUDPPort = udpPort;
                    lblTCPPort->Text = tcpPort.ToString();
                    lblUDPPort->Text = udpPort.ToString();

                    AppState::Instance()->TCPPort = tcpPort;
                    AppState::Instance()->UDPPort = udpPort;

                    AddLog(String::Format(L"TCP Port changed to: {0}, UDP Port changed to: {1}", tcpPort, udpPort), 0);

                    if (RemoteServer_IsRunning() && AskForRestart(L"TCP/UDP Port change"))
                        PerformRestart();
                }
            }
            delete dialog;
        }


        void ShowEditUUIDDialog()
        {
            Form^ dialog = gcnew Form();
            dialog->Text = L"Edit Bluetooth UUID";
            dialog->Size = Drawing::Size(450, 160);
            dialog->FormBorderStyle = System::Windows::Forms::FormBorderStyle::FixedDialog;
            dialog->MaximizeBox = false;
            dialog->MinimizeBox = false;
            dialog->StartPosition = FormStartPosition::CenterParent;
            dialog->BackColor = Color::White;

            Label^ label = gcnew Label();
            label->Text = L"UUID:";
            label->Location = Drawing::Point(15, 18);
            label->Size = Drawing::Size(50, 20);
            label->TextAlign = ContentAlignment::MiddleRight;

            TextBox^ txtUUID = gcnew TextBox();
            txtUUID->Location = Drawing::Point(75, 15);
            txtUUID->Size = Drawing::Size(345, 23);
            txtUUID->Text = lblBluetoothUUID->Text;
            txtUUID->TextAlign = HorizontalAlignment::Center;
            txtUUID->Font = gcnew Drawing::Font(L"Consolas", 9);

            Label^ lblHint = gcnew Label();
            lblHint->Text = L"Format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx";
            lblHint->Location = Drawing::Point(75, 43);
            lblHint->Size = Drawing::Size(345, 15);
            lblHint->ForeColor = Color::Gray;
            lblHint->Font = gcnew Drawing::Font(L"Microsoft Sans Serif", 8);

            Panel^ pnlBottom = gcnew Panel();
            pnlBottom->BackColor = Color::FromArgb(240, 240, 240);
            pnlBottom->Dock = DockStyle::Bottom;
            pnlBottom->Height = 48;

            Button^ btnOK = gcnew Button();
            btnOK->Text = L"OK";
            btnOK->Location = Drawing::Point(255, 10);
            btnOK->Size = Drawing::Size(80, 28);
            btnOK->DialogResult = System::Windows::Forms::DialogResult::OK;

            Button^ btnCancel = gcnew Button();
            btnCancel->Text = L"Cancel";
            btnCancel->Location = Drawing::Point(345, 10);
            btnCancel->Size = Drawing::Size(80, 28);
            btnCancel->DialogResult = System::Windows::Forms::DialogResult::Cancel;

            pnlBottom->Controls->Add(btnOK);
            pnlBottom->Controls->Add(btnCancel);

            dialog->Controls->Add(label);
            dialog->Controls->Add(txtUUID);
            dialog->Controls->Add(lblHint);
            dialog->Controls->Add(pnlBottom);

            dialog->AcceptButton = btnOK;
            dialog->CancelButton = btnCancel;

            if (dialog->ShowDialog() == System::Windows::Forms::DialogResult::OK)
            {
                String^ newUUID = txtUUID->Text->Trim();
                if (String::IsNullOrEmpty(newUUID))
                {
                    MessageBox::Show(L"UUID cannot be empty!", L"Error", MessageBoxButtons::OK, MessageBoxIcon::Error);
                    return;
                }

                if (newUUID->Length != 36 || newUUID->Split('-')->Length != 5)
                {
                    MessageBox::Show(L"Invalid UUID format!\nUse format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
                        L"Error", MessageBoxButtons::OK, MessageBoxIcon::Error);
                    return;
                }

                AppState::Instance()->BluetoothUUID = newUUID;
                lblBluetoothUUID->Text = newUUID;

                if (AskForRestart(L"Bluetooth UUID change"))
                    PerformRestart();

                AddLog(String::Format(L"UUID changed to: {0}", newUUID), 0);
            }
            delete dialog;
        }

        // ===== Label Click Handlers =====
        void lblIP_Click(Object^ sender, EventArgs^ e)
        {
            LoadLocalIP();
            MessageBox::Show(L"IP Address: " + lblLocalIP->Text, L"IP Address", MessageBoxButtons::OK, MessageBoxIcon::Information);
        }

        void lblTCP_Click(Object^ sender, EventArgs^ e)
        {
            ShowEditPortsDialog();
        }


        void lblBluetooth_Click(Object^ sender, EventArgs^ e)
        {
            btnEnableBluetooth->PerformClick();
        }

        void lblPass_Click(Object^ sender, EventArgs^ e)
        {
            btnNeedPassword->PerformClick();
        }

        void lblPassword_Click(Object^ sender, EventArgs^ e)
        {
            btnNeedPassword->PerformClick();
        }

        void lblTCPPort_Click(Object^ sender, EventArgs^ e)
        {
            ShowEditPortsDialog();
        }

        void lblUDPPort_Click(Object^ sender, EventArgs^ e)
        {
            ShowEditPortsDialog();
        }


        void lblBluetoothUUID_Click(Object^ sender, EventArgs^ e)
        {
            if (btnEnableBluetooth->Tag != nullptr && (bool)btnEnableBluetooth->Tag)
                ShowEditUUIDDialog();
            else
                AddLog(L"Please enable Bluetooth first to edit UUID!", 1);
        }

        void lblLocalIP_Click(Object^ sender, EventArgs^ e)
        {
            LoadLocalIP();
        }

        // ===== Button Event Handlers =====

        void OnActionServerClicked(Object^ sender, EventArgs^ e)
        {
            if (RemoteServer_IsRunning())
            {
                StopServer();
            }
            else
            {
                StartServer();
            }
        }

        void OnClearLogClicked(Object^ sender, EventArgs^ e)
        {
            txtLog->Clear();
        }

        void OnNeedPasswordClicked(Object^ sender, EventArgs^ e)
        {
            AppState^ state = AppState::Instance();

            if (!state->NeedPassword)
            {
                if (String::IsNullOrEmpty(state->Password))
                {
                    Random^ rnd = gcnew Random();
                    int defaultPass = rnd->Next(1000, 9999);
                    String^ defaultPassword = defaultPass.ToString();
                    state->Password = defaultPassword;
                    lblPassword->Text = defaultPassword;
                    MessageBox::Show(String::Format(L"Default password: {0}\nPlease remember!", defaultPassword),
                        L"Default Password", MessageBoxButtons::OK, MessageBoxIcon::Information);
                    AddLog(String::Format(L"Default password generated: {0}", defaultPassword), 0);
                }

                state->NeedPassword = true;
                UpdatePasswordButton(true);
                AddLog(L"Password required ENABLED", 0);
                if (AskForRestart(L"Password enable")) PerformRestart();
                if (trayManager != nullptr)
                    trayManager->ShowMessage(L"Password Required", L"Password required is now ON", ToolTipIcon::Info);
            }
            else
            {
                if (MessageBox::Show(L"Disable Password requirement?", L"Disable Password",
                    MessageBoxButtons::YesNo, MessageBoxIcon::Question) == System::Windows::Forms::DialogResult::Yes)
                {
                    state->NeedPassword = false;
                    UpdatePasswordButton(false);
                    AddLog(L"Password required DISABLED", 0);
                    if (AskForRestart(L"Password disable")) PerformRestart();
                    if (trayManager != nullptr)
                        trayManager->ShowMessage(L"Password Required", L"Password requirement disabled", ToolTipIcon::Info);
                }
            }
        }

        void OnEnableBluetoothClicked(Object^ sender, EventArgs^ e)
        {
            AppState^ state = AppState::Instance();

            if (!state->BluetoothEnabled)
            {
                if (String::IsNullOrEmpty(state->BluetoothUUID))
                {
                    ShowEditUUIDDialog();
                    return;
                }

                state->BluetoothEnabled = true;
                UpdateBluetoothButton(true);
                AddLog(L"Bluetooth enabled", 0);
                if (AskForRestart(L"Bluetooth enable")) PerformRestart();
                if (trayManager != nullptr)
                    trayManager->ShowMessage(L"Bluetooth Enabled", L"Bluetooth is now ON", ToolTipIcon::Info);
            }
            else
            {
                if (MessageBox::Show(L"Disable Bluetooth requirement?", L"Disable Bluetooth",
                    MessageBoxButtons::YesNo, MessageBoxIcon::Question) == System::Windows::Forms::DialogResult::Yes)
                {
                    state->BluetoothEnabled = false;
                    UpdateBluetoothButton(false);
                    AddLog(L"Bluetooth disabled", 0);
                    if (AskForRestart(L"Bluetooth disable")) PerformRestart();
                    if (trayManager != nullptr)
                        trayManager->ShowMessage(L"Bluetooth Disabled", L"Bluetooth is now OFF", ToolTipIcon::Info);
                }
            }
        }

        void OnWiFiClicked(Object^ sender, EventArgs^ e)
        {
            LoadLocalIP();
        }

        void OnAutoRunChanged(Object^ sender, EventArgs^ e)
        {
            AppState::Instance()->AutoRunWithWindows = chkAutoRunWithWindows->Checked;
            if (trayManager != nullptr)
                trayManager->UpdateAutoRunWithWindows(chkAutoRunWithWindows->Checked);
        }

        void OnAutoStartServerChanged(Object^ sender, EventArgs^ e)
        {
            AppState::Instance()->AutoStartServer = chkAutoStartServer->Checked;
            if (trayManager != nullptr)
                trayManager->UpdateAutoStartServer(chkAutoStartServer->Checked);
            if (chkAutoStartServer->Checked && !RemoteServer_IsRunning()) StartServer();
        }

        // ===== CHỈ 1 TIMER DUY NHẤT =====
        void OnMainTimerTick(Object^ sender, EventArgs^ e)
        {
            if (isExiting) return;

            bool running = RemoteServer_IsRunning();
            if (running != isServerRunning)
                UpdateServerStatusUI(running);

            if (running)
            {
                int tcpCount = 0;
                int btCount = 0;

                int total = RemoteServer_GetConnectionStats(&tcpCount, &btCount);

                if (total != currentConnections)
                    UpdateConnectionCountUI(total, tcpCount, btCount);

                UpdateConnectionStats(total, tcpCount, btCount);
            }

            const char* log = RemoteServer_PopLog();
            if (log != nullptr)
            {
                String^ logMsg = gcnew String(log);
                if (!String::IsNullOrEmpty(logMsg)) AddLog(logMsg, 0);
            }
        }

        // ===== Tray Event Handlers =====

        void OnTrayStartServer(Object^ sender, EventArgs^ e)
        {
            StartServer();
        }

        void OnTrayStopServer(Object^ sender, EventArgs^ e)
        {
            StopServer();
        }

        void OnTrayShowWindow(Object^ sender, EventArgs^ e)
        {
            // ❗ Đảm bảo chạy trên UI thread nếu TrayManager gọi từ thread khác
            if (this->InvokeRequired)
            {
                this->Invoke(gcnew EventHandler(this, &MainForm::OnTrayShowWindow), sender, e);
                return;
            }

            // ❗ Show() trước, rồi mới set Normal (tránh kẹt trạng thái Minimized)
            this->Show();
            this->WindowState = FormWindowState::Normal;
            this->BringToFront();
            this->Activate();
            this->Focus();
        }

        void OnTrayDoubleClick(Object^ sender, EventArgs^ e)
        {
            OnTrayShowWindow(sender, e);
        }

        void OnTrayExit(Object^ sender, EventArgs^ e)
        {
            if (MessageBox::Show(L"Are you sure you want to exit?\n\nThe server will be stopped automatically.",
                L"Confirm Exit", MessageBoxButtons::YesNo, MessageBoxIcon::Question, MessageBoxDefaultButton::Button2)
                == System::Windows::Forms::DialogResult::Yes)
            {
                isExiting = true;
                minimizeToTray = false;
                AddLog(L"Application exiting...", 0);

                if (mainTimer != nullptr)
                {
                    mainTimer->Stop();
                }

                if (trayManager != nullptr)
                {
                    trayManager->IsVisible = false;
                }

                if (RemoteServer_IsRunning())
                {
                    AddLog(L"Stopping server...", 0);
                    RemoteServer_Stop();
                    for (int i = 0; i < 20 && RemoteServer_IsRunning(); i++)
                    {
                        Thread::Sleep(50);
                        Application::DoEvents();
                    }
                }

                AddLog(L"Cleaning up resources...", 0);
                RemoteServer_Cleanup();
                Thread::Sleep(100);
                AddLog(L"Application shutdown complete.", 0);
                this->Close();
                Application::Exit();
            }
        }


        void OnTrayAutoRunClicked(Object^ sender, EventArgs^ e)
        {
            if (trayManager != nullptr)
            {
                chkAutoRunWithWindows->Checked = trayManager->AutoRunWithWindowsChecked;
                AppState::Instance()->AutoRunWithWindows = trayManager->AutoRunWithWindowsChecked;
            }
        }

        void OnTrayAutoStartClicked(Object^ sender, EventArgs^ e)
        {
            if (trayManager != nullptr)
            {
                chkAutoStartServer->Checked = trayManager->AutoStartServerChecked;
                AppState::Instance()->AutoStartServer = trayManager->AutoStartServerChecked;
                if (trayManager->AutoStartServerChecked && !RemoteServer_IsRunning()) StartServer();
            }
        }
    };  // END CLASS
}  // END NAMESPACE