package com.luomin.studydemo;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;

import com.luomin.dubbing.Dubbing;
import com.luomin.learnbinder.MusicPlayerService;

import java.io.File;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String Dir = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "3E口语" + File.separator + "dubbing" + File.separator + "1099" + File.separator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.hello).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Log.e("TAG", "onClick: start");
                ArrayList<String> recordPcms = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    recordPcms.add(Dir + "1099" + i + ".pcm");
                }
//                Dubbing.sumAllProgressWithSrt(Dir, "cut", Dir + "1099material.aac", Dir + "material.srt", recordPcms, Dir + "1099material.mp4", Dir + "merge.mp4");
                Dubbing.sumAllProgressWithSrtAlreadyBackgroundPcm(Dir, "cut", Dir + "background.pcm", Dir + "material.srt", recordPcms, Dir + "1099material.mp4", Dir + "merge.mp4");

                Log.e("TAG", "onClick: end");
            }
        });

//        new MusicPlayerService();
    }
}
