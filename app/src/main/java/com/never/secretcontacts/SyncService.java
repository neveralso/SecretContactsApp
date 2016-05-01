package com.never.secretcontacts;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.SystemClock;
import android.util.Log;

import com.never.secretcontacts.util.AlarmReceiver;
import com.never.secretcontacts.util.Contact;
import com.never.secretcontacts.util.ContactsManager;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SyncService extends Service{

    private Binder sync_binder_ = new SyncBinder();


    @Override
    public void onCreate() {
        Log.i("service", "service create");
    }

    @Override
    public Binder onBind(Intent intent) {
        return sync_binder_;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i("service", "service unbind");

        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int start_id) {
        Log.i("service", "service on start command");
        Log.i("service", "executed at " + new Date().toString());
        syncProcess();
        startSyncAlarmer();
        return super.onStartCommand(intent, flags, start_id);
    }

    private void startSyncAlarmer() {
        Log.i("service", "starting alarmer.");
        AlarmManager manager = (AlarmManager) getSystemService(ALARM_SERVICE);
        int next_time = 1000 * 20;
        long trigger_time = SystemClock.elapsedRealtime() + next_time;
        Intent i = new Intent(this, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, i, 0);
        manager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger_time, pi);
        Log.i("service", "finish starting alarmer.");
    }

    private static final Integer g_sync_task_lock_ = 0;
    private static SyncTask g_sync_task_ = null;
    private void syncProcess() {
        Log.i("service", "sync process start");
        synchronized (g_sync_task_lock_) {
            if (g_sync_task_ != null)return;
            g_sync_task_ = new SyncTask();
        }
        g_sync_task_.execute((Void) null);

        g_sync_task_ = null;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.i("service", "service rebind");
    }

    @Override
    public void onDestroy() {
        Log.i("service", "service destory");
    }

    class SyncBinder extends Binder {
        public void syncNow() {
            Log.i("service", "sync now");
            syncProcess();
        }

        public SyncService getService() {
            return SyncService.this;
        }
    }



    public class SyncTask extends AsyncTask<Void, Void, Integer> {

        @Override
        protected Integer doInBackground(Void... params) {
            try {

                if (!MyApp.checkKeyStatus() || !MyApp.checkKeyStatus()) {
                    return -3;
                }

                JSONObject json = MyApp.getAuthJson();
                if (json == null) {
                    return -1;
                }
                JSONObject resp_json = MyApp.HttpPostJson(MyApp.URL_POLLING, json);
                if(resp_json == null) {
                    return -2;
                }
                int status_code = resp_json.getInt("status_code");
                Log.i("http", "code " + status_code);
                if(status_code == HttpURLConnection.HTTP_OK) {
                    return processPollingList(resp_json);
                }
                else if (status_code == HttpURLConnection.HTTP_FORBIDDEN) {
                    return -3;
                }
                else {
                    return -2;
                }
            }
            catch (Exception e) {
                Log.e("network", "Network failed." + e.getMessage());
            }

            return -2;
        }

        private Integer processPollingList(JSONObject json) {
            Map<String, List<String>> contacts_map =
                    MyApp.contacts_manager_.getAllContactsMap();
            Map<String, Integer> contacts_map_from_server =
                    getContactsMapFromJson(json);
            if (contacts_map_from_server == null) return -1;
            Integer success_counter = 0;
            String id, content;
            Integer last_op, last_op_time;
            for (Map.Entry<String, List<String>> entry : contacts_map.entrySet()) {
                id = entry.getKey();
                content = entry.getValue().get(0);
                last_op_time = Integer.valueOf(entry.getValue().get(1));
                last_op = Integer.valueOf(entry.getValue().get(2));
                if(last_op == ContactsManager.OP.DELETE.getValue()) {
                    if (pushToServer(id, last_op_time, content, true)) success_counter++;
                    if (contacts_map_from_server.containsKey(id)) {
                        contacts_map_from_server.remove(id);
                    }
                }
                else {
                    if (contacts_map_from_server.containsKey(id)) {
                        if (contacts_map_from_server.get(id) > last_op_time) {
                            if (pullFromServer(id, true))success_counter++;
                        }
                        else if (contacts_map_from_server.get(id) < last_op_time) {
                            if (pushToServer(id, last_op_time, content, false)) success_counter++;
                        }
                        contacts_map_from_server.remove(id);
                    }
                    else {
                        if (last_op == ContactsManager.OP.NEW.getValue()){
                            if (pushToServer(id, last_op_time, content, false)) success_counter++;
                        }
                        else if (last_op == ContactsManager.OP.SYNCED.getValue()){
                            MyApp.contacts_manager_.deleteContact(id);
                        }
                    }
                }
            }
            for (Map.Entry<String, Integer> entry : contacts_map_from_server.entrySet()) {
                id = entry.getKey();
                if (pullFromServer(id, false)) success_counter++;
            }
            return success_counter;
        }

        private Boolean pushToServer(String id, Integer last_op_time, String content, Boolean delete) {
            try {
                Log.i("service", "push to server, id " + id + " , " + content);
                JSONObject json = MyApp.getAuthJson();
                if (json == null) {
                    return false;
                }
                String encrypted_content = MyApp.key_manager_.encryptByPublicKey(content);
                json.put("action", "push");
                json.put("id", id);
                json.put("last_op_time", last_op_time);
                if (!delete)json.put("content", encrypted_content);
                json.put("delete", delete);
                JSONObject resp_json = MyApp.HttpPostJson(MyApp.URL_CONTACTS, json);
                if(resp_json == null) {
                    return false;
                }
                int status_code = resp_json.getInt("status_code");
                Log.i("http", "code " + status_code);
                if (status_code == HttpURLConnection.HTTP_OK) {
                    if (delete) {
                        MyApp.contacts_manager_.deleteContact(id);
                    }
                    else {
                        MyApp.contacts_manager_.setContactOP(id, ContactsManager.OP.SYNCED, false);
                    }
                    return true;
                }
                else {
                    return false;
                }
            }
            catch (Exception e) {
                Log.e("network", "Network failed." + e.getMessage());
                return false;
            }
        }

        private Boolean pullFromServer(String id, Boolean update) {
            try {
                Log.i("service", "pull from server, id " + id);
                JSONObject json = MyApp.getAuthJson();
                if (json == null) {
                    return false;
                }
                json.put("action", "pull");
                json.put("id", id);
                JSONObject resp_json = MyApp.HttpPostJson(MyApp.URL_CONTACTS, json);
                if(resp_json == null) {
                    return false;
                }
                int status_code = resp_json.getInt("status_code");
                Log.i("http", "code " + status_code);
                if (status_code == HttpURLConnection.HTTP_OK) {
                    String decrypted_content = MyApp.key_manager_.decryptByPrivateKey(
                            resp_json.getString("content")
                    );
                    if (update) {
                        MyApp.contacts_manager_.updateContactFromServer(
                                Contact.loadContactFromJsonString(decrypted_content),
                                resp_json.getInt("last_op_time")
                        );
                    }
                    else {
                        MyApp.contacts_manager_.createContactFromServer(
                                Contact.loadContactFromJsonString(decrypted_content),
                                resp_json.getInt("last_op_time")
                        );
                    }
                    return true;
                }
                return false;
            }
            catch (Exception e) {
                Log.e("network", "Network failed." + e.getMessage());
                return false;
            }
        }

        private Map<String, Integer> getContactsMapFromJson(JSONObject json) {
            try {
                JSONObject json_contacts_map = json.getJSONObject("contacts_map");
                Iterator<String> iter = json_contacts_map.keys();

                Map<String, Integer> contacts_map_from_server = new TreeMap<>();
                String temp_key;
                while (iter.hasNext()) {
                    temp_key = iter.next();
                    contacts_map_from_server.put(
                            temp_key,
                            json_contacts_map.getInt(temp_key)
                    );
                }
                return contacts_map_from_server;
            }
            catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(final Integer res) {
            g_sync_task_ = null;

            Log.i("service", "task finish, res " + res.toString());
            if (res > 0) {
                Intent i = new Intent("UPDATE_UI");
                sendBroadcast(i);
            }
            else if (res == -1){
                Log.i("service", "unknown error. ");
            }
            else if (res == -2){
                Log.i("service", "network error. ");
            }
            else if (res == -3){
                Log.i("service", "auth failed. ");
            }
        }

        @Override
        protected void onCancelled() {
            g_sync_task_ = null;
            synchronized (g_sync_task_lock_) {
                if (g_sync_task_ != null)return;
                g_sync_task_ = new SyncTask();
            }
        }
    }

}
