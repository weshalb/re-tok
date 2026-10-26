package app.commentbubble;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Saved comments, kept on the phone in SharedPreferences. */
final class Store {
    static final String EXPORT_PREFIX = "cb1:";

    static final class Item {
        String id;
        String link;
        String who;
        String note;
        Set<String> texts = new HashSet<>();
        String creator;
        String caption;
        String sound;
        boolean mine;
        long createdAt;

        Matcher.VideoKey key() {
            return creator == null ? null : new Matcher.VideoKey(creator, caption, sound);
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("link", link);
            o.put("who", who);
            o.put("note", note);
            o.put("texts", new JSONArray(texts));
            o.put("createdAt", createdAt);
            if (creator != null) o.put("creator", creator);
            if (caption != null) o.put("caption", caption);
            if (sound != null) o.put("sound", sound);
            o.put("mine", mine);
            return o;
        }

        static Item fromJson(JSONObject o) throws JSONException {
            Item it = new Item();
            it.id = o.optString("id", UUID.randomUUID().toString());
            it.link = o.getString("link");
            it.who = o.optString("who", "");
            it.note = o.optString("note", "");
            JSONArray a = o.optJSONArray("texts");
            if (a != null) for (int i = 0; i < a.length(); i++) it.texts.add(a.getString(i));
            it.createdAt = o.optLong("createdAt", System.currentTimeMillis());
            it.creator = o.optString("creator", null);
            it.caption = o.optString("caption", null);
            it.sound = o.optString("sound", null);
            it.mine = o.optBoolean("mine", true);
            return it;
        }

        String name() {
            if (mine) return "Du";
            return who.isEmpty() ? "Freund*in" : who;
        }

        String label() {
            return note.isEmpty() ? name() + " hat einen Kommentar repostet" : name() + ": " + note;
        }

        /** Creator and caption of the video, for list screens. */
        String videoLabel() {
            if (creator == null) return "Video";
            return caption == null ? creator : creator + " · " + caption;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("store", Context.MODE_PRIVATE);
    }

    static synchronized List<Item> all(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("items", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Item.fromJson(a.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
        return out;
    }

    private static void save(Context c, List<Item> items) {
        JSONArray a = new JSONArray();
        for (Item it : items) {
            try {
                a.put(it.toJson());
            } catch (JSONException ignored) {
            }
        }
        prefs(c).edit().putString("items", a.toString()).apply();
    }

    static synchronized void add(Context c, Item item) {
        List<Item> items = all(c);
        items.removeIf(x -> x.id.equals(item.id));
        items.add(0, item);
        save(c, items);
    }

    static synchronized void delete(Context c, String id) {
        List<Item> items = all(c);
        items.removeIf(x -> x.id.equals(id));
        save(c, items);
    }

    static Item newItem(String link, String who, String note, Collection<String> texts, Matcher.VideoKey video) {
        Item it = new Item();
        it.id = UUID.randomUUID().toString();
        it.link = link;
        it.who = who;
        it.note = note;
        it.texts.addAll(texts);
        it.mine = true;
        if (video != null && video.valid()) {
            it.creator = video.creator;
            it.caption = video.caption;
            it.sound = video.sound;
        }
        it.createdAt = System.currentTimeMillis();
        return it;
    }

    /** Text a friend can paste or share into their own copy of the app. */
    static String export(Item it) throws JSONException {
        String b64 = Base64.encodeToString(it.toJson().toString().getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP | Base64.URL_SAFE);
        String from = it.who.isEmpty() ? "Ich" : it.who;
        String note = it.note.isEmpty() ? "" : " „" + it.note + "“";
        return from + " hat einen Kommentar repostet" + note + "\n" + it.link
                + "\nIn ReTok öffnen: " + EXPORT_PREFIX + b64;
    }

    /** Returns the item embedded in a shared text, or null. */
    static Item parseExport(String text) {
        int i = text.indexOf(EXPORT_PREFIX);
        if (i < 0) return null;
        int end = i + EXPORT_PREFIX.length();
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) end++;
        try {
            byte[] raw = Base64.decode(text.substring(i + EXPORT_PREFIX.length(), end), Base64.URL_SAFE);
            Item it = Item.fromJson(new JSONObject(new String(raw, StandardCharsets.UTF_8)));
            it.mine = false;
            return it;
        } catch (IllegalArgumentException | JSONException e) {
            return null;
        }
    }
}
