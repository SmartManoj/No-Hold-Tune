package com.smart.noholdtone;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.TelecomManager;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Setup (become the default phone app), a dial pad (needed for the DIALER role), and the event log. */
public class MainActivity extends Activity {
    private TextView checks, log;
    private Button makeDefault;
    private EditText number;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad * 3, pad, pad);

        TextView about = new TextView(this);
        about.setTextSize(15);
        about.setText("When the other person puts you on hold, your side holds too, so you don't hear "
                + "their hold tune. When they come back, your side resumes automatically.\n\n"
                + "Android only lets the default phone app follow calls, so No Hold Tune works as your "
                + "phone app (dial pad + call screen).\n");
        col.addView(about);

        makeDefault = new Button(this);
        makeDefault.setText("Set as default phone app");
        makeDefault.setOnClickListener(v -> startActivityForResult(
                getSystemService(RoleManager.class).createRequestRoleIntent(RoleManager.ROLE_DIALER), 2));
        col.addView(makeDefault);

        number = new EditText(this);
        number.setHint("Phone number");
        number.setInputType(InputType.TYPE_CLASS_PHONE);
        number.setTextSize(22);
        col.addView(number);
        Button call = new Button(this);
        call.setText("Call");
        call.setOnClickListener(v -> placeCall());
        col.addView(call);

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
        Insets.apply(sv);
        setContentView(sv);
        fillFromIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        fillFromIntent(i);
    }

    /** ACTION_DIAL (tel:…) from other apps pre-fills the number, as a dialer must. */
    private void fillFromIntent(Intent i) {
        Uri data = i == null ? null : i.getData();
        if (data != null && "tel".equals(data.getScheme())) number.setText(data.getSchemeSpecificPart());
    }

    private void placeCall() {
        String n = number.getText().toString().trim();
        if (n.isEmpty()) return;
        if (checkSelfPermission("android.permission.CALL_PHONE") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.CALL_PHONE"}, 3);
            return;
        }
        getSystemService(TelecomManager.class).placeCall(Uri.fromParts("tel", n, null), new Bundle());
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == 3 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) placeCall();
    }

    @Override protected void onResume() {
        super.onResume();
        ui.post(refresh);
    }

    @Override protected void onPause() {
        ui.removeCallbacks(refresh);
        super.onPause();
    }

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            boolean isDefault = getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_DIALER);
            makeDefault.setVisibility(isDefault ? View.GONE : View.VISIBLE);
            checks.setText((isDefault ? "✅ Default phone app\n" : "❌ Not the default phone app yet\n")
                    + (CallService.bound
                    ? "✅ Receiving calls from Android\n"
                    : "⏳ Not seen a call yet (normal until your next call)\n"));
            StringBuilder sb = new StringBuilder();
            synchronized (CallService.LOG) { for (String s : CallService.LOG) sb.append(s).append('\n'); }
            log.setText(sb);
            ui.postDelayed(this, 1000);
        }
    };
}
