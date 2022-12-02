package com.ss.video.rtc.demo.quickstart;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.ss.bytertc.engine.RTCEngine;
import com.ss.rtc.demo.quickstart.R;

import java.util.TreeMap;
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

        findViewById(R.id.join_room_btn).setOnClickListener((v) -> {
            String roomId = roomInput.getText().toString();
            String userId = userInput.getText().toString();
            String codeId = codeInput.getText().toString();

            joinChannel(roomId, userId, codeId);
            //if (room.containsKey(roomId)){
            //    String code_exist = room.get(roomId);
            //    if (code_exist.equals(codeId)) joinChannel(roomId, userId, codeId);
            //    else Toast.makeText(this,"Error code",Toast.LENGTH_LONG).show();
            //}
            //else{
            //    room.put(roomId, codeId);
            //    joinChannel(roomId, userId, codeId);
            //    //Toast.makeText(this,"room does not exist",Toast.LENGTH_LONG).show();
            //}

        });
        // 获取当前SDK的版本号
        String SDKVersion = RTCEngine.getSdkVersion();
        //TextView versionTv = findViewById(R.id.version_tv);
        //versionTv.setText(String.format("VolcEngineRTC v%s", SDKVersion));

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                1000);
    }

    private void joinChannel(String roomId, String userId, String codeId) {
        if (TextUtils.isEmpty(roomId)) {
            Toast.makeText(this, "房间号不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(userId)) {
            Toast.makeText(this, "用户名不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(codeId)) {
            Toast.makeText(this, "密码不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Pattern.matches(Constants.INPUT_REGEX, roomId)) {
            Toast.makeText(this, "房间号格式错误", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Pattern.matches(Constants.INPUT_REGEX, userId)) {
            Toast.makeText(this, "用户名格式错误", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Pattern.matches(Constants.INPUT_REGEX, codeId)) {
            Toast.makeText(this, "密码格式错误", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, RTCRoomActivity.class);
        intent.putExtra(Constants.ROOM_ID_EXTRA, roomId);
        intent.putExtra(Constants.USER_ID_EXTRA, userId);
        intent.putExtra(Constants.CODE_ID_EXTRA, codeId);
        startActivity(intent);

    }
}