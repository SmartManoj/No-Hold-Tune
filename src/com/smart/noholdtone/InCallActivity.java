package com.smart.noholdtone;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.VideoProfile;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** In-call screen: caller, state, and Answer / End / Hold / Mute / Speaker / Keypad. */
public class InCallActivity extends Activity {
    private TextView who, state;
    private Button answer, end, hold, mute, speaker, keys;
    private GridLayout keypad;

    static Intent intent(Context c) {
        return new Intent(c, InCallActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    static String caller(Call call) {
        Uri h = call.getDetails().getHandle();
        String name = call.getDetails().getContactDisplayName();
        if (name != null && !name.isEmpty()) return name;
        return h == null ? "Unknown" : h.getSchemeSpecificPart();
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(pad, pad * 4, pad, pad);

        who = new TextView(this);
        who.setTextSize(30);
        who.setGravity(Gravity.CENTER);
        col.addView(who);
        state = new TextView(this);
        state.setTextSize(18);
        state.setGravity(Gravity.CENTER);
        state.setPadding(0, pad / 2, 0, pad * 2);
        col.addView(state);

        LinearLayout row1 = row(col), row2 = row(col);
        mute = button(row1, "Mute", v -> {
            CallService s = CallService.instance;
            CallAudioState a = s == null ? null : s.getCallAudioState();
            if (a != null) s.setMuted(!a.isMuted());
        });
        speaker = button(row1, "Speaker", v -> {
            CallService s = CallService.instance;
            CallAudioState a = s == null ? null : s.getCallAudioState();
            if (a == null) return;
            boolean on = a.getRoute() == CallAudioState.ROUTE_SPEAKER;
            int other = (a.getSupportedRouteMask() & CallAudioState.ROUTE_BLUETOOTH) != 0
                    ? CallAudioState.ROUTE_BLUETOOTH : CallAudioState.ROUTE_WIRED_OR_EARPIECE;
            s.setAudioRoute(on ? other : CallAudioState.ROUTE_SPEAKER);
        });
        hold = button(row2, "Hold", v -> {
            Call c = CallService.current;
            if (c == null) return;
            if (c.getDetails().getState() == Call.STATE_HOLDING) c.unhold(); else c.hold();
        });
        keys = button(row2, "Keypad", v -> keypad.setVisibility(keypad.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        keypad = new GridLayout(this);
        keypad.setColumnCount(3);
        keypad.setVisibility(View.GONE);
        for (String d : new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"}) {
            Button k = new Button(this);
            k.setText(d);
            k.setTextSize(22);
            k.setOnTouchListener((v, e) -> { // tone lasts while the key is held
                Call c = CallService.current;
                if (c == null) return false;
                if (e.getAction() == MotionEvent.ACTION_DOWN) c.playDtmfTone(d.charAt(0));
                else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) c.stopDtmfTone();
                return false;
            });
            keypad.addView(k);
        }
        col.addView(keypad);

        LinearLayout row3 = row(col);
        row3.setPadding(0, pad * 2, 0, 0);
        answer = button(row3, "Answer", v -> {
            Call c = CallService.current;
            if (c != null) c.answer(VideoProfile.STATE_AUDIO_ONLY);
        });
        end = button(row3, "End", v -> {
            Call c = CallService.current;
            if (c == null) return;
            if (c.getDetails().getState() == Call.STATE_RINGING) c.reject(false, null); else c.disconnect();
        });
        answer.setBackgroundColor(0xFF2E7D32);
        end.setBackgroundColor(0xFFC62828);
        answer.setTextColor(0xFFFFFFFF);
        end.setTextColor(0xFFFFFFFF);
        who.setTypeface(Typeface.DEFAULT_BOLD);

        Insets.apply(col);
        setContentView(col);
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(r, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private Button button(LinearLayout row, String text, View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(18);
        btn.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(8, 8, 8, 8);
        row.addView(btn, lp);
        return btn;
    }

    @Override protected void onResume() {
        super.onResume();
        CallService.uiListener = () -> runOnUiThread(this::refresh);
        refresh();
    }

    @Override protected void onPause() {
        CallService.uiListener = null;
        super.onPause();
    }

    private void refresh() {
        Call c = CallService.current;
        if (c == null) { finish(); return; }
        int s = c.getDetails().getState();
        who.setText(caller(c));
        state.setText(CallService.stateName(s));
        boolean ringing = s == Call.STATE_RINGING;
        answer.setVisibility(ringing ? View.VISIBLE : View.GONE);
        end.setText(ringing ? "Decline" : "End");
        hold.setEnabled(s == Call.STATE_ACTIVE || s == Call.STATE_HOLDING);
        hold.setText(s == Call.STATE_HOLDING ? "Resume" : "Hold");
        CallService svc = CallService.instance;
        CallAudioState a = svc == null ? null : svc.getCallAudioState();
        mute.setText(a != null && a.isMuted() ? "Unmute" : "Mute");
        speaker.setText(a != null && a.getRoute() == CallAudioState.ROUTE_SPEAKER ? "Earpiece" : "Speaker");
        for (Button btn : new Button[]{mute, speaker, keys}) btn.setEnabled(!ringing);
    }
}
