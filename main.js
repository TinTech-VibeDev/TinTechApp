const { app, BrowserWindow, shell, Menu, dialog, nativeImage, ipcMain } = require("electron");
const path = require("path");
const fs = require("fs");

const START_URL = process.env.FILMBUFF_URL || "https://f.filmbuff.ir/menu";
const HOME_URL = process.env.FILMBUFF_HOME || "https://f.filmbuff.ir/";

let mainWindow = null;
let playerWindow = null;

function appIconNative() {
  const ico = path.join(__dirname, "assets", "icon.ico");
  const png = path.join(__dirname, "assets", "icon.png");
  try {
    if (fs.existsSync(ico)) return nativeImage.createFromPath(ico);
    if (fs.existsSync(png)) return nativeImage.createFromPath(png);
  } catch (_) {}
  return undefined;
}

/** Copy .ico next to userData so Windows shortcuts can read it (asar-safe). */
function shortcutIconPath() {
  const dest = path.join(app.getPath("userData"), "filmbuff-icon.ico");
  const candidates = [
    path.join(__dirname, "assets", "icon.ico"),
    path.join(__dirname, "build", "icon.ico"),
    path.join(process.resourcesPath || "", "assets", "icon.ico"),
    path.join(process.resourcesPath || "", "build", "icon.ico"),
  ];
  for (const src of candidates) {
    try {
      if (src && fs.existsSync(src)) {
        fs.mkdirSync(path.dirname(dest), { recursive: true });
        fs.copyFileSync(src, dest);
        return dest;
      }
    } catch (_) {}
  }
  return process.execPath;
}

function createDesktopShortcut() {
  if (process.platform !== "win32") return;
  try {
    const desktop = app.getPath("desktop");
    const shortcutPath = path.join(desktop, "FilmBuff.lnk");
    const icon = shortcutIconPath();
    const ops = {
      target: process.execPath,
      cwd: path.dirname(process.execPath),
      args: "",
      description: "FilmBuff Desktop",
      icon,
      iconIndex: 0,
    };
    // Electron native API — more reliable than PowerShell for portable builds
    if (typeof shell.writeShortcutLink === "function") {
      const ok = shell.writeShortcutLink(shortcutPath, "create", ops);
      if (!ok) shell.writeShortcutLink(shortcutPath, "update", ops);
      return;
    }
  } catch (e) {
    console.error("shortcut error", e);
  }
}

function isPlayUrl(url) {
  try {
    const u = new URL(url);
    if (u.pathname === "/play" || u.pathname.endsWith("/play")) return true;
  } catch (_) {}
  return false;
}

function isMediaUrl(url) {
  return /\.(mp4|m3u8|mkv|webm)(\?|$)/i.test(url || "");
}

function openInternalPlayer(rawUrl) {
  let video = "";
  let sub = "";
  let title = "FilmBuff";
  try {
    const u = new URL(rawUrl, START_URL);
    if (u.pathname === "/play" || u.pathname.endsWith("/play")) {
      video = u.searchParams.get("u") || u.searchParams.get("url") || u.searchParams.get("video") || "";
      sub = u.searchParams.get("sub") || u.searchParams.get("vtt") || u.searchParams.get("srt") || "";
      title = u.searchParams.get("title") || title;
    } else {
      video = u.href;
    }
  } catch (_) {
    video = rawUrl;
  }
  if (!video) {
    dialog.showErrorBox("پلیر", "لینک ویدیو معتبر نیست.");
    return;
  }

  const qs = new URLSearchParams();
  qs.set("u", video);
  if (sub) qs.set("sub", sub);
  if (title) qs.set("title", title);

  if (playerWindow && !playerWindow.isDestroyed()) {
    playerWindow.focus();
    playerWindow.loadFile(path.join(__dirname, "player.html"), { search: qs.toString() });
    return;
  }

  const icon = appIconNative();
  playerWindow = new BrowserWindow({
    width: 1000,
    height: 562,
    minWidth: 800,
    minHeight: 450,
    backgroundColor: "#0a0a0a",
    title: title + " — FilmBuff",
    icon: icon,
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, "player-preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

  playerWindow.loadFile(path.join(__dirname, "player.html"), { search: qs.toString() });
  playerWindow.on("closed", () => {
    playerWindow = null;
  });
}

function buildMenu() {
  Menu.setApplicationMenu(
    Menu.buildFromTemplate([
      {
        label: "FilmBuff",
        submenu: [
          { label: "خانه", click: () => mainWindow && mainWindow.loadURL(HOME_URL) },
          { label: "منوی فیلم‌ها", click: () => mainWindow && mainWindow.loadURL(START_URL) },
          { type: "separator" },
          {
            label: "ایجاد میانبر دسکتاپ",
            click: () => {
              createDesktopShortcut();
              dialog.showMessageBox({
                type: "info",
                title: "FilmBuff",
                message: "میانبر FilmBuff روی دسکتاپ ایجاد/به‌روز شد.",
              });
            },
          },
          { type: "separator" },
          { role: "quit", label: "خروج" },
        ],
      },
      {
        label: "نمایش",
        submenu: [
          { role: "reload", label: "بارگذاری مجدد" },
          { role: "togglefullscreen", label: "تمام‌صفحه" },
          { role: "toggleDevTools", label: "ابزار توسعه‌دهنده" },
        ],
      },
    ])
  );
}

function createWindow() {
  const icon = appIconNative();
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 720,
    minWidth: 960,
    minHeight: 560,
    backgroundColor: "#0a0a0a",
    title: "FilmBuff",
    icon: icon,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

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
    if (isPlayUrl(url) || isMediaUrl(url)) {
      openInternalPlayer(url);
      return { action: "deny" };
    }
    if (/^(https?:|vlc:|potplayer:|mailto:)/i.test(url)) {
      shell.openExternal(url).catch(() => {});
    }
    return { action: "deny" };
  });

  mainWindow.webContents.on("will-navigate", (event, url) => {
    try {
      if (isPlayUrl(url) || isMediaUrl(url)) {
        event.preventDefault();
        openInternalPlayer(url);
        return;
      }
      const u = new URL(url);
      const host = u.hostname.toLowerCase();
      const allowed =
        host === "f.filmbuff.ir" ||
        host.endsWith(".filmbuff.ir") ||
        host.endsWith(".workers.dev");
      if (!allowed && /^https?:$/i.test(u.protocol)) {
        event.preventDefault();
        shell.openExternal(url).catch(() => {});
      }
    } catch (_) {}
  });

  mainWindow.webContents.on("did-navigate", (_e, url) => {
    if (isPlayUrl(url)) {
      openInternalPlayer(url);
      mainWindow.loadURL(START_URL).catch(() => {});
    }
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

ipcMain.on("player-close", () => {
  if (playerWindow && !playerWindow.isDestroyed()) playerWindow.close();
});

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
    try {
      app.setAppUserModelId("ir.tintech.filmbuff.desktop");
    } catch (_) {}
    buildMenu();
    createWindow();
    // Always (re)write desktop shortcut with correct icon
    setTimeout(() => createDesktopShortcut(), 800);
  });

  app.on("window-all-closed", () => {
    if (process.platform !== "darwin") app.quit();
  });
}
