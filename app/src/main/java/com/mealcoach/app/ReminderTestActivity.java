package com.mealcoach.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class ReminderTestActivity extends Activity {
    private final Handler handler=new Handler();

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );
        setContentView(R.layout.activity_test_alarm);

        int stage=getIntent().getIntExtra("stage",5);
        boolean finalMode=getIntent().getBooleanExtra("final_mode",false);
        ReminderTestManager.markFullScreenSeen(this,stage);

        TextView level=findViewById(R.id.testAlarmLevel);
        TextView title=findViewById(R.id.testAlarmTitle);
        TextView body=findViewById(R.id.testAlarmBody);
        Button close=findViewById(R.id.testAlarmClose);

        level.setText("ТЕСТ · ЕТАП "+stage+"/7");
        title.setText(finalMode?"FINAL ALARM ВІДКРИВСЯ":"FULL-SCREEN ВІДКРИВСЯ");
        body.setText("Якщо ти бачиш цей екран, full-screen частина тесту реально спрацювала. Він закриється сам через кілька секунд.");
        close.setOnClickListener(v->finish());

        handler.postDelayed(this::finish,6500L);
    }

    @Override protected void onDestroy(){
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
