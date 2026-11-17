package com.cepgarson.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Yaz\u0131c\u0131 eklentisi (Wi-Fi / Bluetooth / Sistem yazd\u0131rma)
        registerPlugin(CgPrinterPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
