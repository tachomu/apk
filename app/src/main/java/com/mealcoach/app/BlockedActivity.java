package com.mealcoach.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

public class BlockedActivity extends Activity {
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_blocked);

        boolean eating=MealEngine.prefs(this).getBoolean(MealEngine.K_EATING,false);
        Button food=findViewById(R.id.blockFoodButton);
        TextView message=findViewById(R.id.blockMessage);

        if(eating){
            message.setText("Соцмережі залишаються заблокованими, доки ти не завершиш поточний прийом їжі.");
            food.setText("ПОВЕРНУТИСЯ ДО ПРИЙОМУ");
        }else{
            message.setText("Ти дійшов до штрафного рівня. Сфотографуй їжу і почни прийом. Блок зніметься тільки після завершення їжі.");
            food.setText("СФОТОГРАФУВАТИ ЇЖУ → ПОЧАТИ");
        }

        food.setOnClickListener(v->{
            Intent i=new Intent(this,MainActivity.class);
            if(!eating)i.putExtra("force_camera",true);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });
    }
}
