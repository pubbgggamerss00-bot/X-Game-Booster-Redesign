package com.bytecycle.xgamebooster.vpn;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.bytecycle.xgamebooster.R;
import com.bytecycle.xgamebooster.ui.MainActivity;
import com.bytecycle.xgamebooster.util.Prefs;

/**
 * Ping booster engine: a VPN tunnel (TUN device) that intercepts DNS
 * queries (UDP:53), probes candidate DNS servers, picks the fastest one
 * and relays every query to it. Only DNS server routes are claimed by the
 * tunnel so the rest of the traffic keeps flowing normally.
 */
public class PingVpnService extends VpnService {

    public static final String ACTION_START = "com.bytecycle.xgamebooster.VPN_START";
    public static final String ACTION_STOP = "com.bytecycle.xgamebooster.VPN_STOP";

    public static volatile boolean running = false;
    public static volatile String bestDns = null;
    public static volatile int bestRtt = -1;
    public static volatile int relayedCount = 0;

    public static final String[] DNS_LIST = {
            "8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "4.2.2.4", "9.9.9.9",
            "178.22.122.100", "185.51.200.2", "10.202.10.10", "10.202.10.11",
            "10.202.10.202"
    };

    private Thread worker;
    private volatile ParcelFileDescriptor tun;
    private volatile FileOutputStream tunOut;
    private volatile boolean quit = false;
    private ExecutorService pool;
    private final Object outLock = new Object();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopAll();
            return START_NOT_STICKY;
        }
        goForeground("در حال پیدا کردن سریع‌ترین DNS…");
        if (!running) {
            running = true;
            quit = false;
            pool = Executors.newCachedThreadPool();
            worker = new Thread() {
                @Override
                public void run() {
                    try {
                        runTunnel();
                    } catch (Throwable t) {
                        Log.e("XGB", "tunnel", t);
                    }
                }
            };
            worker.start();
        }
        return START_STICKY;
    }

    private void runTunnel() {
        try {
            String chosen = Prefs.getStr(this, "dns", "auto");
            String best;
            if ("auto".equals(chosen)) {
                best = probeBest();
            } else {
                best = chosen;
                bestDns = chosen;
                bestRtt = probeOne(chosen);
            }
            if (best == null) {
                best = "8.8.8.8";
            }
            Builder b = new Builder()
                    .setSession("X Ping Tunnel")
                    .setMtu(1500)
                    .addAddress("10.111.222.1", 32)
                    .addDnsServer(best)
                    .addRoute(best, 32);
            for (String d : DNS_LIST) {
                try {
                    b.addRoute(d, 32);
                } catch (Throwable t) { }
            }
            tun = establish();
            if (tun == null) {
                running = false;
                updateNotif("اجازه‌ی VPN داده نشد");
                stopSelf();
                return;
            }
            updateNotif("تونل پینگ فعال — DNS: " + best
                    + (bestRtt >= 0 ? " (" + bestRtt + "ms)" : ""));
            FileInputStream in = new FileInputStream(tun.getFileDescriptor());
            tunOut = new FileOutputStream(tun.getFileDescriptor());
            byte[] pkt = new byte[32768];
            while (!quit && running) {
                int len = in.read(pkt);
                if (len > 0) {
                    handlePacket(pkt, len);
                }
            }
        } catch (Throwable t) {
            Log.e("XGB", "run", t);
        } finally {
            running = false;
        }
    }

    private void handlePacket(byte[] pkt, int len) {
        try {
            if (len < 20) return;
            int ver = (pkt[0] >> 4) & 0xF;
            if (ver != 4) return;
            int ihl = (pkt[0] & 0xF) * 4;
            if (len < ihl + 8) return;
            int proto = pkt[9] & 0xFF;
            if (proto != 17) return; // فقط UDP
            final long src = ip4(pkt, 12), dst = ip4(pkt, 16);
            int off = ihl;
            int sport = ((pkt[off] & 0xFF) << 8) | (pkt[off + 1] & 0xFF);
            int dport = ((pkt[off + 2] & 0xFF) << 8) | (pkt[off + 3] & 0xFF);
            if (dport != 53) return;
            int ulen = ((pkt[off + 4] & 0xFF) << 8) | (pkt[off + 5] & 0xFF);
            int dnsLen = Math.min(ulen - 8, len - off - 8);
            if (dnsLen <= 0) return;
            final byte[] dns = new byte[dnsLen];
            System.arraycopy(pkt, off + 8, dns, 0, dnsLen);
            final byte[] head = new byte[ihl];
            System.arraycopy(pkt, 0, head, 0, ihl);
            final int fport = sport;
            pool.submit(new Runnable() {
                @Override
                public void run() {
                    relay(head, dns, src, fport, dst, head.length);
                }
            });
        } catch (Throwable t) { }
    }

    private void relay(byte[] head, byte[] dns, long src, int sport, long dstIp, int ihl) {
        DatagramSocket s = null;
        try {
            s = new DatagramSocket();
            s.setSoTimeout(5000);
            protect(s);
            DatagramPacket q = new DatagramPacket(dns, dns.length,
                    InetAddress.getByAddress(ipBytes(dstIp)), 53);
            s.send(q);
            byte[] rb = new byte[4096];
            DatagramPacket rp = new DatagramPacket(rb, rb.length);
            try {
                s.receive(rp);
            } catch (SocketTimeoutException e) {
                return;
            }
            int rlen = rp.getLength();
            relayedCount++;
            sendResponse(head, ihl, dstIp, sport, src, rb, rlen);
        } catch (Throwable t) { }
        finally {
            try {
                if (s != null) s.close();
            } catch (Throwable t) { }
        }
    }

    private void sendResponse(byte[] head, int ihl, long dnsIp, int appPort,
                              long appIp, byte[] data, int dlen) {
        try {
            int total = ihl + 8 + dlen;
            byte[] o = new byte[total];
            o[0] = 0x45;
            o[1] = 0;
            o[2] = (byte) ((total >> 8) & 0xFF);
            o[3] = (byte) (total & 0xFF);
            if (head.length >= 6) {
                o[4] = head[4];
                o[5] = head[5];
            }
            o[6] = 0x40;
            o[7] = 0;
            o[8] = 64;
            o[9] = 17;
            o[10] = 0;
            o[11] = 0;
            putIp(o, 12, dnsIp);
            putIp(o, 16, appIp);
            int cs = checksum(o, ihl);
            o[10] = (byte) ((cs >> 8) & 0xFF);
            o[11] = (byte) (cs & 0xFF);
            int uo = ihl;
            o[uo] = 0;
            o[uo + 1] = 53;
            o[uo + 2] = (byte) ((appPort >> 8) & 0xFF);
            o[uo + 3] = (byte) (appPort & 0xFF);
            int ul = 8 + dlen;
            o[uo + 4] = (byte) ((ul >> 8) & 0xFF);
            o[uo + 5] = (byte) (ul & 0xFF);
            o[uo + 6] = 0;
            o[uo + 7] = 0;
            System.arraycopy(data, 0, o, ihl + 8, dlen);
            FileOutputStream fo = tunOut;
            if (fo != null) {
                synchronized (outLock) {
                    fo.write(o);
                    fo.flush();
                }
            }
        } catch (Throwable t) { }
    }

    static int checksum(byte[] b, int hlen) {
        long sum = 0;
        for (int i = 0; i < hlen; i += 2) {
            sum += ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
        }
        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return (int) ((~sum) & 0xFFFF);
    }

    static long ip4(byte[] b, int off) {
        return ((b[off] & 0xFFL) << 24) | ((b[off + 1] & 0xFFL) << 16)
                | ((b[off + 2] & 0xFFL) << 8) | (b[off + 3] & 0xFFL);
    }

    static void putIp(byte[] b, int off, long ip) {
        b[off] = (byte) ((ip >> 24) & 0xFF);
        b[off + 1] = (byte) ((ip >> 16) & 0xFF);
        b[off + 2] = (byte) ((ip >> 8) & 0xFF);
        b[off + 3] = (byte) (ip & 0xFF);
    }

    static byte[] ipBytes(long ip) {
        return new byte[]{
                (byte) ((ip >> 24) & 0xFF), (byte) ((ip >> 16) & 0xFF),
                (byte) ((ip >> 8) & 0xFF), (byte) (ip & 0xFF)
        };
    }

    static long ipStrToLong(String ip) {
        String[] p = ip.split("\\.");
        return ((Long.parseLong(p[0]) & 0xFFL) << 24)
                | ((Long.parseLong(p[1]) & 0xFFL) << 16)
                | ((Long.parseLong(p[2]) & 0xFFL) << 8)
                | (Long.parseLong(p[3]) & 0xFFL);
    }

    private int probeOne(String ip) {
        DatagramSocket s = null;
        try {
            s = new DatagramSocket();
            s.setSoTimeout(2500);
            protect(s);
            byte[] q = dnsQuery();
            DatagramPacket qp = new DatagramPacket(q, q.length,
                    InetAddress.getByAddress(ipBytes(ipStrToLong(ip))), 53);
            long t0 = SystemClock.elapsedRealtime();
            s.send(qp);
            byte[] rb = new byte[512];
            DatagramPacket rp = new DatagramPacket(rb, rb.length);
            s.receive(rp);
            return (int) (SystemClock.elapsedRealtime() - t0);
        } catch (Throwable t) {
            return -1;
        } finally {
            try {
                if (s != null) s.close();
            } catch (Throwable t) { }
        }
    }

    private String probeBest() {
        String best = null;
        int brt = Integer.MAX_VALUE;
        for (String d : DNS_LIST) {
            if (quit) break;
            int r = probeOne(d);
            if (r >= 0 && r < brt) {
                brt = r;
                best = d;
            }
        }
        bestRtt = best == null ? -1 : brt;
        bestDns = best;
        return best;
    }

    static byte[] dnsQuery() {
        byte[] id = {(byte) (new Random().nextInt(256)), (byte) (new Random().nextInt(256))};
        String host = "connectivitycheck.gstatic.com";
        String[] labels = host.split("\\.");
        int total = 12 + 1 + host.length() + 4;
        byte[] q = new byte[total];
        q[0] = id[0];
        q[1] = id[1];
        q[2] = 0x01;
        q[3] = 0x00;
        q[4] = 0;
        q[5] = 1;
        q[6] = 0;
        q[7] = 0;
        q[8] = 0;
        q[9] = 0;
        q[10] = 0;
        q[11] = 0;
        int i = 12;
        for (String lb : labels) {
            q[i++] = (byte) lb.length();
            byte[] lbB = lb.getBytes();
            System.arraycopy(lbB, 0, q, i, lbB.length);
            i += lbB.length;
        }
        q[i++] = 0;
        q[i++] = 0;
        q[i++] = 1;
        q[i++] = 0;
        q[i] = 1;
        return q;
    }

    private void stopAll() {
        quit = true;
        running = false;
        bestDns = null;
        bestRtt = -1;
        try {
            if (tun != null) tun.close();
        } catch (Throwable t) { }
        if (pool != null) pool.shutdownNow();
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    private void goForeground(String txt) {
        Notification n = notif(txt);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(21, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(21, n);
        }
    }

    private void updateNotif(String txt) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(21, notif(txt));
    }

    private Notification notif(String txt) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "vpn")
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_wifi)
                .setContentTitle("X Ping Booster")
                .setContentText(txt)
                .setOngoing(true);
        Intent st = new Intent(this, PingVpnService.class);
        st.setAction(ACTION_STOP);
        PendingIntent pi = PendingIntent.getService(this, 1, st,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        b.addAction(0, "خاموش کردن تونل", pi);
        PendingIntent pi2 = PendingIntent.getActivity(this, 2,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        b.setContentIntent(pi2);
        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(Notification.PRIORITY_LOW);
        }
        return b.build();
    }
}
