package com.never.secretcontacts;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.net.HttpURLConnection;
import java.util.Date;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Request;




public class MyApp extends Application{

    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    public static String URL_SITE = "https://sc.404notfound.top/";

    public static String URL_LOGIN = URL_SITE + "api/login";
    public static String URL_REGISTER = URL_SITE + "api/register";
    public static String URL_POLLING = URL_SITE + "api/polling";

    private static String auth_id_;
    private static String auth_key_;

    private static SharedPreferences shared_preference_;

    @Override
    public void onCreate() {
        super.onCreate();
        shared_preference_ = getApplicationContext().getSharedPreferences("data", MODE_PRIVATE);
        auth_key_ = shared_preference_.getString("auth_key", "");
    }

    public static Boolean checkLoginStatus() {
        return checkLoginExpireDate();
    }

    public static void clearLoginStatus() {
        auth_id_ = "";
        auth_key_ = "";
    }
    public static void updateLoginStatus(String auth_id, String auth_key) {
        auth_id_ = auth_id;
        auth_key_ = auth_key;
        if(checkLoginExpireDate()) {
            SharedPreferences.Editor editor =shared_preference_.edit();
            editor.putString("auth_key", auth_key_);
            editor.apply();
        }
    }

    public static JSONObject getAuthJson() {
        try {
            return new JSONObject().
                    put("auth_id", auth_id_).
                    put("auth_key", auth_key_);
        }
        catch (Exception e) {
            Log.e("auth", "make auth json error. " + e.getMessage());
            return null;
        }
    }

    public static Boolean checkLoginExpireDate() {
        return !auth_key_.equals("");
    }

    public static JSONObject HttpPostJson(String url, JSONObject json) {
        try {
            OkHttpClient client = new OkHttpClient();
            RequestBody body = RequestBody.create(JSON, json.toString());
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();
            Response response = client.newCall(request).execute();
            if(response.code() == HttpURLConnection.HTTP_OK) {
                JSONTokener json_tokener = new JSONTokener(response.body().string());
                response.body().close();
                return ((JSONObject)json_tokener.nextValue()).
                        put("status_code", response.code());
            }
            else {
                response.body().close();
                return new JSONObject().put("status_code", response.code());
            }
        }
        catch (Exception e) {
            Log.e("http", "http post json failed. " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }


}