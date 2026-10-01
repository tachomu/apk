package com.mealcoach.app;

import android.app.Activity;
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

        Button sit = findViewById(R.id.alarmSitButton);
        Button ate = findViewById(R.id.alarmAteButton);

        sit.setOnClickListener(v -> {
            MealEngine.startEating(this);
            finish();
        });

        ate.setOnClickListener(v -> {
            MealEngine.ate(this);
            finish();
        });
    }
}
