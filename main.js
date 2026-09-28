const { app, BrowserWindow, shell, Menu, dialog, nativeImage } = require("electron");
const path = require("path");
const fs = require("fs");
const { spawn } = require("child_process");

/** Public site (change if needed) */
const START_URL = process.env.FILMBUFF_URL || "https://f.filmbuff.ir/menu";
const HOME_URL = process.env.FILMBUFF_HOME || "https://f.filmbuff.ir/";

let mainWindow = null;
let shortcutDone = false;

function iconPath() {
  const ico = path.join(__dirname, "assets", "icon.ico");
  const png = path.join(__dirname, "assets", "icon.png");
  if (fs.existsSync(ico)) return ico;
  if (fs.existsSync(png)) return png;
  return undefined;
}

function userDataFlagPath() {
  return path.join(app.getPath("userData"), "desktop-shortcut-v1.flag");
}

function createDesktopShortcutWindows() {
  if (process.platform !== "win32") return;
  try {
    if (fs.existsSync(userDataFlagPath())) {
      shortcutDone = true;
      return;
    }
  } catch (_) {}

  const desktop = app.getPath("desktop");
  const shortcutPath = path.join(desktop, "FilmBuff.lnk");
  const target = process.execPath;
  const workDir = path.dirname(target);
  const ico = iconPath() || target;

  // PowerShell COM shortcut — works for installed and portable builds
  const ps = `
$ErrorActionPreference = 'Stop'
$WshShell = New-Object -ComObject WScript.Shell
$Shortcut = $WshShell.CreateShortcut(${JSON.stringify(shortcutPath)})
$Shortcut.TargetPath = ${JSON.stringify(target)}
$Shortcut.WorkingDirectory = ${JSON.stringify(workDir)}
$Shortcut.IconLocation = ${JSON.stringify(ico + ",0")}
$Shortcut.Description = "FilmBuff Desktop"
$Shortcut.Save()
`;
  const encoded = Buffer.from(ps, "utf16le").toString("base64");
  const child = spawn(
    "powershell.exe",
    ["-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded],
    { windowsHide: true }
  );
  child.on("close", (code) => {
    if (code === 0) {
      try {
        fs.mkdirSync(path.dirname(userDataFlagPath()), { recursive: true });
        fs.writeFileSync(userDataFlagPath(), new Date().toISOString(), "utf8");
        shortcutDone = true;
      } catch (_) {}
    }
  });
}

function buildMenu() {
  const template = [
    {
      label: "FilmBuff",
      submenu: [
        {
          label: "خانه",
          click: () => mainWindow && mainWindow.loadURL(HOME_URL),
        },
        {
          label: "منوی فیلم‌ها",
          click: () => mainWindow && mainWindow.loadURL(START_URL),
        },
        { type: "separator" },
        {
          label: "بازنشانی زوم",
          accelerator: "CmdOrCtrl+0",
          click: () => mainWindow && mainWindow.webContents.setZoomFactor(1),
        },
        {
          label: "بزرگ‌نمایی",
          accelerator: "CmdOrCtrl+Plus",
          click: () => {
            if (!mainWindow) return;
            const z = mainWindow.webContents.getZoomFactor();
            mainWindow.webContents.setZoomFactor(Math.min(2, z + 0.1));
          },
        },
        {
          label: "کوچک‌نمایی",
          accelerator: "CmdOrCtrl+-",
          click: () => {
            if (!mainWindow) return;
            const z = mainWindow.webContents.getZoomFactor();
            mainWindow.webContents.setZoomFactor(Math.max(0.7, z - 0.1));
          },
        },
        { type: "separator" },
        { role: "reload", label: "بارگذاری مجدد" },
        { role: "toggleDevTools", label: "ابزار توسعه‌دهنده" },
        { type: "separator" },
        { role: "quit", label: "خروج" },
      ],
    },
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

function createWindow() {
  const ico = iconPath();
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 900,
    minHeight: 600,
    title: "FilmBuff",
    backgroundColor: "#07070a",
    autoHideMenuBar: true,
    icon: ico,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      spellcheck: false,
    },
  });

  // Identify as native desktop app so site enables internal player path if needed
  const ses = mainWindow.webContents.session;
  ses.webRequest.onBeforeSendHeaders((details, callback) => {
    const headers = { ...details.requestHeaders };
    const ua = headers["User-Agent"] || "";
    if (!/FilmBuffApp/i.test(ua)) {
      headers["User-Agent"] = ua + " FilmBuffApp FilmBuff/Native FilmBuffDesktop/" + app.getVersion();
    }
    callback({ requestHeaders: headers });
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    // External schemes (VLC, browser downloads, etc.)
    if (/^(https?:|vlc:|potplayer:|mailto:)/i.test(url)) {
      shell.openExternal(url).catch(() => {});
      return { action: "deny" };
    }
    return { action: "deny" };
  });

  mainWindow.webContents.on("will-navigate", (event, url) => {
    try {
      const u = new URL(url);
      const allowed = ["f.filmbuff.ir", "filmbuff.ir"];
      // keep same-origin navigation in webview; open unknown hosts externally
      const host = u.hostname.toLowerCase();
      const ok =
        allowed.some((h) => host === h || host.endsWith("." + h)) ||
        host.endsWith(".workers.dev");
      if (!ok && /^https?:$/i.test(u.protocol)) {
        event.preventDefault();
        shell.openExternal(url).catch(() => {});
      }
    } catch (_) {}
  });

  mainWindow.loadURL(START_URL).catch((err) => {
    dialog.showErrorBox(
      "خطا در اتصال",
      "نمی‌توان به سرور FilmBuff وصل شد.\n\n" + String(err && err.message ? err.message : err)
    );
  });

  mainWindow.on("closed", () => {
    mainWindow = null;
  });
}

const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on("second-instance", () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }
  });

  app.whenReady().then(() => {
    if (iconPath()) {
      try {
        app.setAppUserModelId("ir.tintech.filmbuff.desktop");
      } catch (_) {}
    }
    buildMenu();
    createWindow();
    // Create desktop shortcut after window is up (Windows)
    setTimeout(() => createDesktopShortcutWindows(), 1500);
  });

  app.on("window-all-closed", () => {
    if (process.platform !== "darwin") app.quit();
  });
}
