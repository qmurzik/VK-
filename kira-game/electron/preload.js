const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('kiraApp', { quit: () => ipcRenderer.send('quit') });
