const { app, BrowserWindow, shell } = require('electron');
const path = require('node:path');
function createWindow() {
  const win = new BrowserWindow({
    width: 1440, height: 900, minWidth: 900, minHeight: 620,
    backgroundColor: '#eef2f6',
    autoHideMenuBar: true,
    webPreferences: { contextIsolation: true, nodeIntegration: false }
  });
  win.loadFile(path.join(__dirname, 'index.html'));
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://rayzenyt147.itch.io')) shell.openExternal(url);
    return { action: 'deny' };
  });
}
app.whenReady().then(() => { createWindow(); app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); }); });
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
