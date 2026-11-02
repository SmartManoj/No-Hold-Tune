package com.smart.noholdtone;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.InCallService;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * The default-dialer InCallService (Play-compliant: the app holds the DIALER role, no restricted
 * permissions). Shows our in-call screen, and when the other side holds, we hold too, so their
 * hold tune isn't heard; when they take us off hold while we are holding, we unhold.
 */
public class CallService extends InCallService {
    static volatile boolean bound;
    static volatile CallService instance;
    static volatile Call current;       // the call shown on InCallActivity
    static volatile Runnable uiListener; // InCallActivity's refresh, run on every call change
    static final ArrayDeque<String> LOG = new ArrayDeque<>();
    // android.telecom.Connection.EVENT_CALL_REMOTELY_(UN)HELD are @hide; Telecom forwards them verbatim.
    private static final String REMOTELY_HELD = "android.telecom.event.CALL_REMOTELY_HELD";
    private static final String REMOTELY_UNHELD = "android.telecom.event.CALL_REMOTELY_UNHELD";

    static void log(String s) {
        String t = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        synchronized (LOG) {
            LOG.addFirst(t + "  " + s);
            while (LOG.size() > 60) LOG.removeLast();
        }
        android.util.Log.i("NoHoldTune", s);
    }

    // Measured on Jio VoLTE (MTK IMS), 2026-11-02:
    // - unhold sent at once after their UNHELD looks fine locally (ACTIVE in ~1.5 s), but the two
    //   re-INVITEs collide: their side stayed on hold (our audio left receive-only, seen on a real
    //   call). So unhold waits SETTLE_MS too.
    // - hold sent at once after their HELD always fails (CALL_HOLD_FAILED ~0.3 s later, not
    //   forwarded to companion apps). Delay probe, their HELD -> our SET_HOLD: 0.3 s -> 2.2 s,
    //   0.6 s -> 1.5 s, 0.9 s -> 1.8 s, 1.2 s -> 2.2 s; so 0.6 s, used for both hold and unhold.
    // - a second request while the first is still in flight is harmful: unhold on an already
    //   active call becomes a Telecom "swap" and re-holds it. So re-check only after VERIFY_MS,
    //   which is longer than any confirmation seen.
    private static final long SETTLE_MS = 600, VERIFY_MS = 3000;
    private static final int MAX_TRIES = 4;
    private final Handler h = new Handler(Looper.getMainLooper());
    private volatile boolean remoteHeld;
    private Boolean target; // side we're driving our call to: true = hold, false = active, null = idle
    private int tries;
    private long since;     // when the other side's event arrived

    private final Call.Callback callback = new Call.Callback() {
        @Override public void onConnectionEvent(Call call, String event, Bundle extras) {
            if (REMOTELY_HELD.equals(event)) {
                remoteHeld = true;
                log("they put you on hold");
                if (autoHold()) start(call, true);
            } else if (REMOTELY_UNHELD.equals(event)) {
                remoteHeld = false;
                log("they're back");
                if (call.getDetails().getState() == Call.STATE_HOLDING) start(call, false);
            }
        }

        @Override public void onStateChanged(Call call, int state) {
            log("call " + stateName(state));
            showCallNotification(call);
            refreshUi();
            if (target != null && state == (target ? Call.STATE_HOLDING : Call.STATE_ACTIVE)) {
                log((target ? "your side is on hold" : "your side resumed")
                        + " (" + (System.currentTimeMillis() - since) + " ms after their change, " + tries + " tries)");
                stop();
            }
        }
    };

    private void start(Call call, boolean hold) {
        stop();
        target = hold;
        since = System.currentTimeMillis();
        h.postDelayed(() -> drive(call), SETTLE_MS); // let their re-INVITE finish first
    }

    private void stop() {
        h.removeCallbacksAndMessages(null);
        target = null;
        tries = 0;
    }

    /** One hold/unhold attempt toward {@link #target}, as long as the other side's state still calls for it. */
    private void drive(Call call) {
        if (target == null) return;
        boolean hold = target;
        if (remoteHeld != hold) { stop(); return; } // they changed their mind meanwhile
        int state = call.getDetails().getState();
        if (state == (hold ? Call.STATE_HOLDING : Call.STATE_ACTIVE)) { stop(); return; }
        if (state != (hold ? Call.STATE_ACTIVE : Call.STATE_HOLDING)) { stop(); return; } // ended, dialing, etc.
        if (++tries > MAX_TRIES) {
            log("gave up " + (hold ? "holding" : "resuming"));
            stop();
            return;
        }
        if (hold) call.hold(); else call.unhold();
        h.postDelayed(() -> drive(call), VERIFY_MS);
    }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("call", "Calls", NotificationManager.IMPORTANCE_HIGH));
    }

    @Override public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override public void onCallAdded(Call call) {
        bound = true;
        current = call;
        log("watching call (" + stateName(call.getDetails().getState()) + ")");
        remoteHeld = false;
        call.registerCallback(callback);
        showCallNotification(call);
        // Incoming calls surface through the full-screen notification; others open the screen directly.
        if (call.getDetails().getState() != Call.STATE_RINGING) startActivity(InCallActivity.intent(this));
    }

    @Override public void onCallRemoved(Call call) {
        stop();
        call.unregisterCallback(callback);
        if (call == current) current = null;
        getSystemService(NotificationManager.class).cancel(2);
        log("call removed");
        refreshUi();
    }

    @Override public void onCallAudioStateChanged(android.telecom.CallAudioState state) {
        refreshUi();
    }

    private static void refreshUi() {
        Runnable r = uiListener;
        if (r != null) r.run();
    }

    /** Ongoing-call notification; while ringing it is a full-screen intent, so the screen shows over the lock screen. */
    private void showCallNotification(Call call) {
        int state = call.getDetails().getState();
        if (state == Call.STATE_DISCONNECTED) return;
        PendingIntent open = PendingIntent.getActivity(this, 0, InCallActivity.intent(this),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = new Notification.Builder(this, "call")
                .setSmallIcon(android.R.drawable.sym_action_call)
                .setContentTitle(InCallActivity.caller(call))
                .setContentText(stateName(state))
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setContentIntent(open);
        if (state == Call.STATE_RINGING) b.setFullScreenIntent(open, true);
        getSystemService(NotificationManager.class).notify(2, b.build());
    }

    static String stateName(int s) { // Call.stateToString is @hide
        switch (s) {
            case Call.STATE_RINGING: return "ringing";
            case Call.STATE_DIALING: return "dialing";
            case Call.STATE_CONNECTING: return "connecting";
            case Call.STATE_ACTIVE: return "active";
            case Call.STATE_HOLDING: return "on hold";
            case Call.STATE_DISCONNECTED: return "ended";
            default: return "state " + s;
        }
    }

    private boolean autoHold() {
        return getSharedPreferences("p", MODE_PRIVATE).getBoolean("auto_hold", true);
    }

}
