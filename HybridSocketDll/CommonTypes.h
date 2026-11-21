#pragma once

enum class LogType {
    L_INFO,
    L_WARN,
    L_ERROR
};


enum class TransportType {
    UNKNOWN,
    TCP,
    BLUETOOTH
};

enum class SessionState {
    IDLE,
    HANDSHAKE,
    AUTH_WAIT,
    STREAM,
    DISCONNECTED
};


enum class SessionRole {
    UNKNOWN = 0,
    COMMAND = 1,   // Tương ứng byte 0x01
    TRANSFER = 2,   // Tương ứng byte 0x02
    SCREEN_STREAM = 3   // Tương ứng byte 0x03
};

enum class OpCode {
    UNKNOWN,
    PING,

    // Mouse & Drag
    MOS,
    ABMOS,
    DRAG,

    // Keyboard
    KEY,
    UTF8,

    // PowerPoint
    PPT,
    WPPT,

    // System
    RUNCMD,

    // Media Control
    PLAYER,
    MEDIA,
    BROWSER,

    // Window Control
    ZOOMOUT,
    ZOOMIN,
    WINDOW,

    // Sound Control
    MUTE,
    VOLUMEUP,
    VOLUMEDN,

    // ============ FILE EXPLORER OPCODES ============
    REFRESHEXPLORER,    // refresh file list
    OPENFILE,           // open file/ folder
    COPYFILE,           // copy file/folder
    CUTFILE,            // cut file/folder
    PASTEFILE,          // paste file/folder
    DELETEFILE,         // delete file/folder
    DETAILSFILE,        // get file details
    UPLOADFILE,         // upload file to PC
    RENAMEFILE,         // rename file/folder
    CREATEFILE,         // create new file
    CREATEFOLDER,       // create new folder
    DOWNLOADFILE,       // download file from PC
    UPLOADFOLDERZIP,    // upload folder as ZIP
    DOWNLOADFOLDERZIP,  // download folder as ZIP
    GETFOLDERINFO,      // get folder info (size, file count)
    FILEEXPLORER,       // browse directory

    // Transfer Control
    STOPTRANSFER,       // stop transfer
    CANCELTRANSFER,     // cancel current transfer
    CANCELIMMEDIATELY,  // immediate cancel


    // ===== STREAMING =====
    STREAM_START,      // Start video stream
    STREAM_STOP,       // Stop video stream
    STREAM_STATUS,     // Get stream status
    STREAM_CONFIG,     // Update stream config

    // Slide Gestures
    SLIDE,
    SLIDEUP,
    SLIDEDOWN,
    SLIDELEFT,
    SLIDERIGHT,

    // Task Management
    GETTASKS,

    // App Management
    GETDOCKINFOAPP,
    GETICONAPP,
    LAUNCHAPP,
    CLOSEAPP,

    // Mouse & Screen
    MOUSESETPOS,
    SCREENMIRROR,
    SCREENCAPTURE,

    // Power Control
    SLEEP,
    LOGOFF,
    REBOOT,
    POWEROFF,
    BRIGHNESSHIGH,
    BRIGHNESSLOW,

    // App Reporting
    REPORTCURRENTAPP,
    DONTREPORTCURRENTAPP
};

