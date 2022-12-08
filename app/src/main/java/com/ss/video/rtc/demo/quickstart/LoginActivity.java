package com.ss.video.rtc.demo.quickstart;

import static com.ss.video.rtc.demo.quickstart.CreateAccountActivity.bytesToHex;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.io.IOUtils;
import com.ss.bytertc.engine.RTCEngine;
import com.ss.rtc.demo.quickstart.R;


import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * VolcEngineRTC 音视频通话入口页面
 *
 * 包含如下简单功能：
 * - 该页面用来跳转至音视频通话主页面
 * - 申请相关权限
 * - 校验房间名和用户名
 * - 展示当前 SDK 使用的版本号 {@link RTCEngine#getSdkVersion()}
 *
 * 有以下常见的注意事项：
 * 1.SDK必要的权限有：外部内存读写、摄像头权限、麦克风权限，其余完整的权限参见{@link /main/AndroidManifest.xml}。
 * 没有这些权限不会导致崩溃，但是会影响SDK的正常使用。
 * 2.SDK 对房间名、用户名的限制是：非空且最大长度不超过128位的数字、大小写字母、@ . _ \ -
 */
public class LoginActivity extends AppCompatActivity {
    private MessageDigest digest;
    private String pwd_sha;
    private URL Url;
    private HttpURLConnection connection;
    private int responseCode;
    private InputStream inputStream;
    private String result = "";
    private Thread thread;
    private final Lock lock = new ReentrantLock();
    private final String OK_REGEX = "\"code\":0";
    private final String NO_USER_REGEX = "\"code\":1";
    private final String PWD_ERR_REGEX = "\"code\":2";

    //第一次点击事件发生的时间
    private long mExitTime;

    //点击两次返回退出app   System.currentTimeMillis()系统当前时间
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if ((System.currentTimeMillis() - mExitTime) > 2000) {
                Object mHelperUtils;
                Toast.makeText(this, "再按一次退出程序", Toast.LENGTH_SHORT).show();
                mExitTime = System.currentTimeMillis();
            } else {
                finish();
                onDestroy();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    private TreeMap<String, String> room = new TreeMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText roomInput = findViewById(R.id.room_id_input);
        EditText userInput = findViewById(R.id.user_id_input);
        EditText codeInput = findViewById(R.id.code_id_input);

        //findViewById(R.id.create_room_btn).setOnClickListener((v) -> {
        //    String roomId = roomInput.getText().toString();
        //    String userId = userInput.getText().toString();
        //    String codeId = codeInput.getText().toString();
        //    if (room.containsKey(roomId)){
        //        Toast.makeText(this,"Room exists",Toast.LENGTH_LONG).show();
        //    }
        //    else{
        //        room.put(roomId, codeId);
        //        joinChannel(roomId, userId, codeId);
        //    }

        //});

        findViewById(R.id.create_account_btn).setOnClickListener((v) ->{
            Intent intent = new Intent(this, CreateAccountActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.join_room_btn).setOnClickListener((v) -> {
            String roomId = roomInput.getText().toString();
            String userId = userInput.getText().toString();
            String codeId = codeInput.getText().toString();
            if(!checkInput(userId, userId, codeId)) return;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                Toast.makeText(this, "Error encoding with SHA-256", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
            byte[] pwd_hash = digest.digest(codeId.getBytes(StandardCharsets.UTF_8));
            pwd_sha = bytesToHex(pwd_hash);

            requestToken(roomId, userId, pwd_sha);
            while(thread.isAlive()){
                if(lock.tryLock()){
                    if(result.equals("")){
                        lock.unlock();
                        continue;
                    }
                    else{
                        lock.unlock();
                        break;
                    }
                }
            }

            while(thread.isAlive()){
                if(lock.tryLock()){
                    if (result.contains(OK_REGEX)){
                        String resSpilt[] = result.split("\"token\":\"");
                        String tokens = resSpilt[1].split("\"")[0];
                        lock.unlock();
                        joinChannel(roomId, userId, tokens);
                        //Toast.makeText(this, tokens, Toast.LENGTH_LONG).show();
                        break;
                        //finish();
                    }
                    if (result.contains(NO_USER_REGEX)){
                        Toast.makeText(this, "Please create an account to enter the room.", Toast.LENGTH_LONG).show();
                        result = new String("");
                        lock.unlock();
                        break;
                    }
                    if (result.contains(PWD_ERR_REGEX)){
                        Toast.makeText(this, "Password is incorrect.", Toast.LENGTH_LONG).show();
                        result = new String("");
                        lock.unlock();
                        break;
                    }

                }
            }

        });
        // 获取当前SDK的版本号
        String SDKVersion = RTCEngine.getSdkVersion();
        //TextView versionTv = findViewById(R.id.version_tv);
        //versionTv.setText(String.format("VolcEngineRTC v%s", SDKVersion));

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                1000);
    }

    private boolean checkInput(String roomId, String userId, String codeId){
        if (TextUtils.isEmpty(roomId)) {
            Toast.makeText(this, "Room cannot be empty", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (TextUtils.isEmpty(userId)) {
            Toast.makeText(this, "User cannot be empty", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (TextUtils.isEmpty(codeId)) {
            Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!Pattern.matches(Constants.INPUT_REGEX, roomId)) {
            Toast.makeText(this, "Room format error", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!Pattern.matches(Constants.INPUT_REGEX, userId)) {
            Toast.makeText(this, "User format error", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!Pattern.matches(Constants.INPUT_REGEX, codeId)) {
            Toast.makeText(this, "Password format error", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private String requestToken(String roomId, String userId, String pwd_sha){

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                String url = "https://oreste.moe:5000/login?user_id=" + userId + "&pwd_sha=" + pwd_sha + "&room_id=" + roomId;
                try { Url = new URL(url);
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
                try { connection = (HttpURLConnection) Url.openConnection();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try { connection.setRequestMethod("GET");
                } catch (ProtocolException e) {
                    e.printStackTrace();
                }
                try { connection.connect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try { responseCode = connection.getResponseCode();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (responseCode == HttpURLConnection.HTTP_OK){
                    try {
                        inputStream = connection.getInputStream();
                        lock.lock();
                        try {
                            result = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                        }
                        finally {
                            lock.unlock();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        thread.start();
        return result;
    }


    private void joinChannel(String roomId, String userId, String tokens) {

        Intent intent = new Intent(this, RTCRoomActivity.class);
        intent.putExtra(Constants.ROOM_ID_EXTRA, roomId);
        intent.putExtra(Constants.USER_ID_EXTRA, userId);
        intent.putExtra(Constants.TOKEN, tokens);
        startActivity(intent);
    }
}