package com.mealcoach.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class ConfirmActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );
        setContentView(R.layout.activity_confirm);

        EditText input = findViewById(R.id.confirmInput);
        Button button = findViewById(R.id.confirmButton);

        button.setOnClickListener(v -> {
            String text = input.getText() == null ? "" : input.getText().toString().trim();
            if ("ПІДТВЕРДЖУЮ".equalsIgnoreCase(text)) {
                DiagnosticStore.log(this, "LATE30_CONFIRMED", "typed confirmation");
                AlarmService.stop(this);
                finish();
            } else {
                Toast.makeText(this, "Введи: ПІДТВЕРДЖУЮ", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onBackPressed() {
        // Intentionally blocked for this escalation screen.
    }
}
