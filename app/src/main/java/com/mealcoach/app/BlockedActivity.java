package com.mealcoach.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

public class BlockedActivity extends Activity {
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_blocked);
        Button food=findViewById(R.id.blockFoodButton);
        food.setOnClickListener(v->{
            Intent i=new Intent(this,MainActivity.class);
            i.putExtra("force_camera",true);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });
    }
}
