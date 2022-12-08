package com.ss.video.rtc.demo.quickstart;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import com.airbnb.lottie.LottieAnimationView;
import com.ss.rtc.demo.quickstart.R;


public class SplashScreen extends AppCompatActivity {

    TextView appname;
    LottieAnimationView lottie;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_splash_screen);
        appname = findViewById(R.id.appname);
        lottie = findViewById(R.id.lottie);

        //lottie.animate().translationX(2000).setDuration(3000).setStartDelay(2000);
        appname.animate().translationY(-500).setDuration(2000).setStartDelay(0);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
                startActivity(intent);
            }
        },3000);

    }
}