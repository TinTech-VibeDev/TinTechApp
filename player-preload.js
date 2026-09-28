const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld("FilmBuffDesktopPlayer", {
  close: () => ipcRenderer.send("player-close"),
});
