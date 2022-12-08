package com.ss.video.rtc.demo.quickstart;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;


import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.io.IOUtils;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.client.HttpClient;
import com.ss.rtc.demo.quickstart.R;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

public class CreateAccountActivity extends AppCompatActivity {
    private MessageDigest digest;
    private String pwd_sha;
    private URL Url;
    private HttpURLConnection connection;
    private int responseCode;
    private InputStream inputStream;
    private String result = "";
    private Thread thread;
    private final Lock lock = new ReentrantLock();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);

        EditText nameInput = findViewById(R.id.create_user_input);
        EditText pwdInput = findViewById(R.id.create_password_input);

        findViewById(R.id.ca_btn).setOnClickListener((v) -> {
            String userID = nameInput.getText().toString();
            String codeId = pwdInput.getText().toString();
            if(!checkInput(userID, codeId)) return;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                Toast.makeText(this, "Error encoding with SHA-256", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
            byte[] pwd_hash = digest.digest(codeId.getBytes(StandardCharsets.UTF_8));
            pwd_sha = bytesToHex(pwd_hash);
            //Toast.makeText(this, pwd_sha, Toast.LENGTH_SHORT).show();
            result = new String("");

            register(userID, pwd_sha);

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
                    if (result.contains("0")){
                        Toast.makeText(this, "Your account has been created.", Toast.LENGTH_LONG).show();
                        lock.unlock();
                        finish();
                    }

                    if (result.contains("1")){
                        Toast.makeText(this, "This account is already created.", Toast.LENGTH_LONG).show();
                        result = new String("");
                        lock.unlock();
                    }
                    break;
                }
            }


            //result = new String("");
            //finish();
            //Toast.makeText(this, result, Toast.LENGTH_LONG).show();
        });

    }

    public static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private boolean checkInput(String userID, String codeID){
        if (TextUtils.isEmpty(userID)) {
            Toast.makeText(this, "user cannot be empty", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (TextUtils.isEmpty(codeID)) {
            Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!Pattern.matches(Constants.INPUT_REGEX, userID)) {
            Toast.makeText(this, "User format error", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!Pattern.matches(Constants.INPUT_REGEX, codeID)) {
            Toast.makeText(this, "Password format error", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }


    private String register(String userID, String pwd_sha) {

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                String url = "https://oreste.moe:5000/register?user_id=" + userID + "&pwd_sha=" + pwd_sha;
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
}