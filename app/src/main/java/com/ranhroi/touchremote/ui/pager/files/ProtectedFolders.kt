package com.ranhroi.touchremote.ui.pager.files

object ProtectedFolders {
    // Windows system folders
    private val windowsSystemFolders = setOf(
        "Windows", "System32", "Program Files", "Program Files (x86)",
        "ProgramData", "Users", "Documents and Settings", "boot",
        "PerfLogs", "Recovery", "System Volume Information",
        "\$Recycle.Bin", "pagefile.sys", "swapfile.sys", "hiberfil.sys", "ADMIN", "Microsoft",
        "Home",
        "Desktop",
        "Documents",
        "Downloads",
        "Music",
        "Videos",
        "Pictures",
        "Temp",
    )

    // Mac system folders
    private val macSystemFolders = setOf(
        "System", "Library", "Applications", "Users",
        "Volumes", "dev", "etc", "tmp", "var", "bin", "sbin",
        "usr", "opt", "private", "cores", "Network"
    )

    // Linux system folders (có thể gặp trên server)
    private val linuxSystemFolders = setOf(
        "boot", "dev", "etc", "home", "lib", "lib64", "media",
        "mnt", "opt", "proc", "root", "run", "sbin", "srv",
        "sys", "tmp", "usr", "var"
    )

    val allProtectedNames: Set<String> =
        windowsSystemFolders + macSystemFolders + linuxSystemFolders
}
