package com.clipcascade.shizuku;

interface IShizukuClipboardService {
    void destroy() = 16777114;
    String readClipboard(int userId) = 1;
    int getServiceUid() = 2;
}
