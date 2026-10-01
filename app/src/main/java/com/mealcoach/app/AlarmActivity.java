package com.mealcoach.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;

public class AlarmActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );
        setContentView(R.layout.activity_alarm);

        Button start = findViewById(R.id.alarmStartButton);
        start.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.putExtra("force_camera", true);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        });
    }

    @Override
    public void onBackPressed() {
        // The final alarm is dismissed by starting the meal, not by Back.
    }
}
