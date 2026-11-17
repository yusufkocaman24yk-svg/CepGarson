package com.cepgarson.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.ContextCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * CepGarson yaz\u0131c\u0131 eklentisi.
 *  - print()        : ESC/POS fi\u015f yaz\u0131c\u0131lara do\u011frudan yazd\u0131r\u0131r (Wi-Fi/LAN :9100 veya Bluetooth SPP)
 *  - listBluetooth(): e\u015fle\u015fmi\u015f Bluetooth cihazlar\u0131 listeler
 *  - scanNetwork()  : yerel a\u011fda 9100 portu a\u00e7\u0131k yaz\u0131c\u0131lar\u0131 arar
 *  - systemPrint()  : Android sistem yazd\u0131rma men\u00fcs\u00fcn\u00fc a\u00e7ar (PDF'e kaydet / servis eklentili yaz\u0131c\u0131lar)
 * JS taraf\u0131:  Capacitor.Plugins.CgPrinter.print({...})
 */
@CapacitorPlugin(
    name = "CgPrinter",
    permissions = { @Permission(alias = "bluetooth", strings = { "android.permission.BLUETOOTH_CONNECT" }) }
)
public class CgPrinterPlugin extends Plugin {

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private WebView printWebView; // \u00e7\u00f6p toplay\u0131c\u0131 silmesin diye tutulur

    // ---------------------------------------------------------------- izinler
    private boolean needsBtPermission() {
        return Build.VERSION.SDK_INT >= 31
            && ContextCompat.checkSelfPermission(getContext(), "android.permission.BLUETOOTH_CONNECT")
               != PackageManager.PERMISSION_GRANTED;
    }

    // ------------------------------------------------------ Bluetooth listesi
    @PluginMethod
    public void listBluetooth(PluginCall call) {
        if (needsBtPermission()) {
            requestPermissionForAlias("bluetooth", call, "btListPermCallback");
            return;
        }
        doListBluetooth(call);
    }

    @PermissionCallback
    private void btListPermCallback(PluginCall call) {
        if (needsBtPermission()) { call.reject("Bluetooth izni verilmedi."); return; }
        doListBluetooth(call);
    }

    private void doListBluetooth(PluginCall call) {
        try {
            BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
            if (ad == null) { call.reject("Bu cihazda Bluetooth yok."); return; }
            if (!ad.isEnabled()) { call.reject("Bluetooth kapal\u0131. L\u00fctfen Bluetooth'u a\u00e7\u0131n."); return; }
            JSArray arr = new JSArray();
            Set<BluetoothDevice> bonded = ad.getBondedDevices();
            if (bonded != null) {
                for (BluetoothDevice d : bonded) {
                    JSObject o = new JSObject();
                    o.put("name", d.getName() == null ? d.getAddress() : d.getName());
                    o.put("address", d.getAddress());
                    arr.put(o);
                }
            }
            JSObject res = new JSObject();
            res.put("devices", arr);
            call.resolve(res);
        } catch (SecurityException e) {
            call.reject("Bluetooth izni yok.");
        } catch (Exception e) {
            call.reject("Bluetooth listesi al\u0131namad\u0131: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------- a\u011f taramas\u0131
    @PluginMethod
    public void scanNetwork(final PluginCall call) {
        final int port = call.getInt("port", 9100);
        new Thread(() -> {
            try {
                String base = localSubnetPrefix();
                if (base == null) { call.reject("Wi-Fi a\u011f\u0131na ba\u011fl\u0131 de\u011filsiniz."); return; }
                final List<String> found = Collections.synchronizedList(new ArrayList<String>());
                ExecutorService pool = Executors.newFixedThreadPool(48);
                for (int i = 1; i <= 254; i++) {
                    final String host = base + i;
                    pool.execute(() -> {
                        Socket s = new Socket();
                        try {
                            s.connect(new InetSocketAddress(host, port), 350);
                            found.add(host);
                        } catch (Exception ignored) {
                        } finally {
                            try { s.close(); } catch (Exception ignored) {}
                        }
                    });
                }
                pool.shutdown();
                pool.awaitTermination(20, TimeUnit.SECONDS);
                JSArray arr = new JSArray();
                Collections.sort(found);
                for (String h : found) arr.put(h);
                JSObject res = new JSObject();
                res.put("hosts", arr);
                call.resolve(res);
            } catch (Exception e) {
                call.reject("A\u011f taramas\u0131 ba\u015far\u0131s\u0131z: " + e.getMessage());
            }
        }).start();
    }

    private String localSubnetPrefix() throws Exception {
        Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
        while (ifs != null && ifs.hasMoreElements()) {
            NetworkInterface ni = ifs.nextElement();
            if (!ni.isUp() || ni.isLoopback()) continue;
            Enumeration<InetAddress> addrs = ni.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress a = addrs.nextElement();
                if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                    String ip = a.getHostAddress();
                    return ip.substring(0, ip.lastIndexOf('.') + 1);
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------ yazd\u0131rma
    @PluginMethod
    public void print(final PluginCall call) {
        if ("bt".equals(call.getString("type", "wifi")) && needsBtPermission()) {
            requestPermissionForAlias("bluetooth", call, "btPrintPermCallback");
            return;
        }
        doPrint(call);
    }

    @PermissionCallback
    private void btPrintPermCallback(PluginCall call) {
        if (needsBtPermission()) { call.reject("Bluetooth izni verilmedi."); return; }
        doPrint(call);
    }

    private void doPrint(final PluginCall call) {
        final String type = call.getString("type", "wifi");
        final String host = call.getString("host", "");
        final int port = call.getInt("port", 9100);
        final String address = call.getString("address", "");
        final String text = call.getString("text", "");
        final String charset = call.getString("charset", "cp857");
        final int codePage = call.getInt("codePage", 13);
        final boolean cut = call.getBoolean("cut", true);

        new Thread(() -> {
            try {
                byte[] data = buildEscPos(text, charset, codePage, cut);
                if ("bt".equals(type)) sendBluetooth(address, data);
                else sendTcp(host, port, data);
                call.resolve();
            } catch (SecurityException e) {
                call.reject("Bluetooth izni yok.");
            } catch (Exception e) {
                String m = e.getMessage() == null ? e.toString() : e.getMessage();
                call.reject("Yaz\u0131c\u0131ya g\u00f6nderilemedi: " + m);
            }
        }).start();
    }

    private byte[] buildEscPos(String text, String charset, int codePage, boolean cut) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] { 0x1B, 0x40 }); // ESC @  : yaz\u0131c\u0131y\u0131 s\u0131f\u0131rla

        if ("cp857".equals(charset)) {
            out.write(new byte[] { 0x1B, 0x74, (byte) codePage }); // ESC t n : kod sayfas\u0131 (T\u00fcrk\u00e7e)
            out.write(encodeCp857(text));
        } else {
            out.write(toAscii(text).getBytes("US-ASCII"));
        }

        out.write(new byte[] { 0x0A, 0x0A, 0x0A });            // 3 sat\u0131r bo\u015fluk
        if (cut) out.write(new byte[] { 0x1D, 0x56, 0x42, 0x00 }); // GS V 66 0 : k\u0131smi kes
        return out.toByteArray();
    }

    // T\u00fcrk\u00e7e karakterler i\u00e7in CP857 tablosu (cihaz\u0131n charset deste\u011fine ba\u011fl\u0131 kalmamak i\u00e7in elle)
    private byte[] encodeCp857(String s) {
        final String from = "\u00c7\u00fc\u00e7\u00e2\u00ee\u0131\u00d6\u00f6\u00dc\u0130\u015e\u015f\u011e\u011f\u00fb";
        final int[] to = { 0x80, 0x81, 0x87, 0x83, 0x8C, 0x8D, 0x99, 0x94, 0x9A, 0x98, 0x9E, 0x9F, 0xA6, 0xA7, 0x96 };
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int idx = from.indexOf(c);
            if (c == 0x20BA) { o.write('T'); o.write('L'); }       // \u20ba -> TL
            else if (idx >= 0) o.write(to[idx]);
            else if (c < 128) o.write(c);
            else o.write('?');
        }
        return o.toByteArray();
    }

    private String toAscii(String s) {
        String from = "\u00e7\u00c7\u011f\u011e\u0131\u0130\u00f6\u00d6\u015f\u015e\u00fc\u00dc\u00e2\u00c2\u00ee\u00ce\u00fb\u00db\u20ba";
        String to   = "cCgGiIoOsSuUaAiIuUT";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            int idx = from.indexOf(c);
            if (c == '\u20ba') sb.append("TL");
            else if (idx >= 0) sb.append(to.charAt(idx));
            else if (c < 128) sb.append(c);
            else sb.append('?');
        }
        return sb.toString();
    }

    private void sendTcp(String host, int port, byte[] data) throws Exception {
        if (host == null || host.trim().isEmpty()) throw new Exception("Yaz\u0131c\u0131 IP adresi bo\u015f.");
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host.trim(), port), 5000);
            s.setSoTimeout(5000);
            OutputStream os = s.getOutputStream();
            os.write(data);
            os.flush();
            Thread.sleep(300);
        } finally {
            try { s.close(); } catch (Exception ignored) {}
        }
    }

    private void sendBluetooth(String address, byte[] data) throws Exception {
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null) throw new Exception("Bu cihazda Bluetooth yok.");
        if (!ad.isEnabled()) throw new Exception("Bluetooth kapal\u0131.");
        BluetoothDevice dev = ad.getRemoteDevice(address);
        try { ad.cancelDiscovery(); } catch (Exception ignored) {}
        BluetoothSocket sock = dev.createRfcommSocketToServiceRecord(SPP_UUID);
        try {
            sock.connect();
            OutputStream os = sock.getOutputStream();
            int chunk = 512;
            for (int i = 0; i < data.length; i += chunk) {
                os.write(data, i, Math.min(chunk, data.length - i));
                os.flush();
                Thread.sleep(25); // ucuz yaz\u0131c\u0131lar\u0131n tamponu ta\u015fmas\u0131n
            }
            Thread.sleep(500);
        } finally {
            try { sock.close(); } catch (Exception ignored) {}
        }
    }

    // ------------------------------------------------- Android sistem yazd\u0131rma
    @PluginMethod
    public void systemPrint(final PluginCall call) {
        final String html = call.getString("html", "");
        final String jobName = call.getString("jobName", "Fis");
        getActivity().runOnUiThread(() -> {
            try {
                WebView wv = new WebView(getActivity());
                printWebView = wv;
                wv.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        PrintManager pm = (PrintManager) getActivity().getSystemService(Context.PRINT_SERVICE);
                        PrintDocumentAdapter adapter = view.createPrintDocumentAdapter(jobName);
                        // 80 mm geni\u015fli\u011finde fi\u015f boyutu
                        PrintAttributes.MediaSize size = new PrintAttributes.MediaSize("cg_receipt80", "Fi\u015f 80mm", 3150, 11000);
                        PrintAttributes attrs = new PrintAttributes.Builder()
                            .setMediaSize(size)
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .build();
                        pm.print(jobName, adapter, attrs);
                        printWebView = null;
                    }
                });
                wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
                call.resolve();
            } catch (Exception e) {
                call.reject("Sistem yazd\u0131rma a\u00e7\u0131lamad\u0131: " + e.getMessage());
            }
        });
    }
}
