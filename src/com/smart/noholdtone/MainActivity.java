package com.smart.noholdtone;

import android.app.Activity;
import android.companion.AssociationInfo;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.IntentSender;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends Activity {
    private TextView checks, log;
    private Button pair;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad * 3, pad, pad);

        TextView about = new TextView(this);
        about.setTextSize(15);
        about.setText("When the other person puts you on hold, your side holds too, so you don't hear "
                + "their hold tune. When they come back, your side resumes automatically.\n\n"
                + "One-time setup: tap Pair and pick any Bluetooth device (e.g. your earphones). "
                + "Android only lets a watch-companion app follow calls; the device is just the key.\n");
        col.addView(about);

        pair = new Button(this);
        pair.setText("Pair (one-time setup)");
        pair.setOnClickListener(v -> pair());
        col.addView(pair);

        Switch autoHold = new Switch(this);
        autoHold.setText("Auto-hold me when they hold (skip their hold tune)");
        autoHold.setChecked(getSharedPreferences("p", MODE_PRIVATE).getBoolean("auto_hold", true));
        autoHold.setOnCheckedChangeListener((v, on) ->
                getSharedPreferences("p", MODE_PRIVATE).edit().putBoolean("auto_hold", on).apply());
        autoHold.setPadding(0, pad, 0, pad);
        col.addView(autoHold);

        checks = new TextView(this);
        checks.setTextSize(15);
        col.addView(checks);

        log = new TextView(this);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextSize(12);
        col.addView(log);

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);
    }

    @Override protected void onResume() {
        super.onResume();
        ui.post(refresh);
    }

    @Override protected void onPause() {
        ui.removeCallbacks(refresh);
        super.onPause();
    }

    /**
     * Watch-profile companion association: Android grants the COMPANION_DEVICE_WATCH role, which
     * includes MANAGE_ONGOING_CALLS, so Telecom binds CallWatchService (no adb needed).
     */
    private void pair() {
        AssociationRequest req = new AssociationRequest.Builder()
                .addDeviceFilter(new BluetoothDeviceFilter.Builder().build())
                .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
                .build();
        getSystemService(CompanionDeviceManager.class).associate(req, getMainExecutor(),
                new CompanionDeviceManager.Callback() {
                    @Override public void onAssociationPending(IntentSender chooser) {
                        try {
                            startIntentSenderForResult(chooser, 2, null, 0, 0, 0);
                        } catch (IntentSender.SendIntentException e) {
                            CallWatchService.log("pairing failed: " + e);
                        }
                    }

                    @Override public void onAssociationCreated(AssociationInfo info) {
                        CallWatchService.log("paired with " + info.getDisplayName());
                    }

                    @Override public void onFailure(CharSequence error) {
                        CallWatchService.log("pairing failed: " + error);
                    }
                });
    }

    private String pairedWith() {
        List<AssociationInfo> a = getSystemService(CompanionDeviceManager.class).getMyAssociations();
        if (a.isEmpty()) return null;
        CharSequence name = a.get(0).getDisplayName();
        return name != null ? name.toString() : String.valueOf(a.get(0).getDeviceMacAddress());
    }

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            String paired = pairedWith();
            pair.setVisibility(paired == null ? View.VISIBLE : View.GONE);
            checks.setText((paired == null ? "❌ Not paired yet (tap Pair)\n" : "✅ Paired with " + paired + "\n")
                    + (CallWatchService.bound
                    ? "✅ Receiving calls from Android\n"
                    : "⏳ Not seen a call yet (normal until your next call)\n"));
            StringBuilder sb = new StringBuilder();
            synchronized (CallWatchService.LOG) { for (String s : CallWatchService.LOG) sb.append(s).append('\n'); }
            log.setText(sb);
            ui.postDelayed(this, 1000);
        }
    };
}
