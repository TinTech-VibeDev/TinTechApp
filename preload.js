const { contextBridge } = require("electron");
contextBridge.exposeInMainWorld("FilmBuffBridge", {
  platform: "desktop",
  isNative: true,
});
contextBridge.exposeInMainWorld("FilmBuffDesktop", {
  version: "1.0.4",
  hasInternalPlayer: true,
});
