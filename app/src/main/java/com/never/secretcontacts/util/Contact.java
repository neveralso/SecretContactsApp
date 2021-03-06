package com.never.secretcontacts.util;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.List;

public class Contact {


    private String id_;
    private String name_;   //显示的数据
    private String sortLetters_;  //显示数据拼音的首字母
    private Boolean block_;

    public List<ContactItem> item_list_ = new ArrayList<>();

    public Contact(String name, String id){
        name_ = name;
        id_ = id;
        block_ = false;
    }

    public static Contact loadContactFromJsonString(String contact_str) {
        try {
            JSONTokener tokener = new JSONTokener(contact_str);
            JSONObject json = (JSONObject) tokener.nextValue();
            Contact contact = new Contact(json.getString("name"), json.getString("id"));
            contact.setBlock(json.getBoolean("block"));
            JSONArray item_arr = json.getJSONArray("items");
            JSONArray tmp_arr;
            for(int i = 0; i < item_arr.length(); ++i) {
                tmp_arr = item_arr.getJSONArray(i);
                contact.item_list_.add(new ContactItem(
                        tmp_arr.getInt(0),
                        tmp_arr.getInt(1),
                        tmp_arr.getString(2)
                ));
            }
            return contact;
        }
        catch (org.json.JSONException e) {
            Log.e("contact", "load contact error.");
            return null;
        }
    }

    public static String dumpContactToJsonString(Contact contact) {
        JSONArray json_items = new JSONArray();

        for (ContactItem item: contact.item_list_) {
            json_items.put(new JSONArray().
                    put(item.type).
                    put(item.inner_type).
                    put(item.content)
            );
        }
        try {
            JSONObject json = new JSONObject().
                    put("name", contact.getName()).
                    put("id", contact.getId()).
                    put("items", json_items).
                    put("block", contact.isBlock());
            return json.toString();
        }
        catch (org.json.JSONException e) {
            Log.e("contact", "dump contact error.");
            return null;
        }

    }

    public Boolean isBlock() {
        return block_;
    }
    public void setBlock(Boolean block) {
        this.block_ = block;
    }
    public String getId() {
        return id_;
    }
    public void setId(String id){
        this.id_ = id;
    }
    public String getName() {
        return name_;
    }
    public void setName(String name) {
        this.name_ = name;
    }
    public String getSortLetters() {
        return sortLetters_;
    }
    public void setSortLetters(String sortLetters) {
        this.sortLetters_ = sortLetters;
    }
}