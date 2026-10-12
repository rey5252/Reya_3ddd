package com.reya.hvylia;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** Small persistent state shared by the YouTube screen, the download service and the player page. */
final class Store {
    private static final String PREFS = "hvylia";
    private static final String KEY_TREE = "download_tree";
    private static final String KEY_KBPS = "download_kbps";
    private static final String KEY_PENDING = "pending_imports";
    private static final String KEY_ALLOWED = "downloaded_uris";

    private Store() { }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Uri tree(Context c) {
        String s = p(c).getString(KEY_TREE, null);
        return s == null ? null : Uri.parse(s);
    }

    static void setTree(Context c, Uri tree) {
        p(c).edit().putString(KEY_TREE, tree.toString()).apply();
    }

    static int kbps(Context c) {
        return p(c).getInt(KEY_KBPS, 192);
    }

    static void setKbps(Context c, int kbps) {
        p(c).edit().putInt(KEY_KBPS, kbps).apply();
    }

    /** Human name of the chosen folder, or null when none is chosen or access was lost. */
    static String treeName(Context c, Uri tree) {
        if (tree == null) return null;
        try {
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
            Cursor cur = c.getContentResolver().query(doc, new String[] { DocumentsContract.Document.COLUMN_DISPLAY_NAME }, null, null, null);
            if (cur == null) return null;
            try { return cur.moveToFirst() ? cur.getString(0) : null; } finally { cur.close(); }
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Queues a saved MP3 for the player page to add to its library the next time it looks. */
    static synchronized void addPending(Context c, Uri doc, String name) {
        SharedPreferences sp = p(c);
        try {
            JSONArray a = new JSONArray(sp.getString(KEY_PENDING, "[]"));
            a.put(new JSONObject().put("uri", doc.toString()).put("name", name));
            Set<String> allowed = new HashSet<>(sp.getStringSet(KEY_ALLOWED, new HashSet<String>()));
            allowed.add(doc.toString());
            sp.edit().putString(KEY_PENDING, a.toString()).putStringSet(KEY_ALLOWED, allowed).apply();
        } catch (JSONException ignored) { }
    }

    /** Returns the queued imports as a JSON array of {uri, name} and clears the queue. */
    static synchronized String takePending(Context c) {
        SharedPreferences sp = p(c);
        String s = sp.getString(KEY_PENDING, "[]");
        sp.edit().putString(KEY_PENDING, "[]").apply();
        return s;
    }

    /** Only files this app downloaded itself may be served to the page. */
    static boolean isAllowed(Context c, String uri) {
        return p(c).getStringSet(KEY_ALLOWED, new HashSet<String>()).contains(uri);
    }
}
