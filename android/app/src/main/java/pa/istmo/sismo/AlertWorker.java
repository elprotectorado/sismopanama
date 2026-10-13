package pa.istmo.sismo;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Revisa sismos cada 15 min aunque la app esté cerrada. Consumo mínimo: una petición pequeña por ciclo. */
public class AlertWorker extends Worker {
    public AlertWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    static void schedule(Context c) {
        Constraints k = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest r = new PeriodicWorkRequest.Builder(AlertWorker.class, 15, TimeUnit.MINUTES).setConstraints(k).build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("istmo-alertas", ExistingPeriodicWorkPolicy.KEEP, r);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        try {
            String base = ctx.getString(R.string.site_url);
            HttpURLConnection c = (HttpURLConnection) new URL(base + "/api/quakes?days=1").openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestProperty("Accept", "application/json");
            if (c.getResponseCode() != 200) return Result.retry();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                String ln;
                while ((ln = br.readLine()) != null) sb.append(ln);
            }
            JSONArray a = new JSONObject(sb.toString()).optJSONArray("quakes");
            if (a == null) return Result.success();

            SharedPreferences sp = ctx.getSharedPreferences("istmo", Context.MODE_PRIVATE);
            double ulat = sp.getFloat("lat", 8.98f), ulon = sp.getFloat("lon", -79.52f);
            boolean first = !sp.getBoolean("init", false);
            Set<String> seen = new HashSet<>(sp.getStringSet("seen", new HashSet<String>()));
            long now = System.currentTimeMillis();
            int sent = 0;
            for (int i = 0; i < a.length(); i++) {
                JSONObject q = a.optJSONObject(i);
                if (q == null) continue;
                String id = q.optString("id");
                long t = q.optLong("t");
                double mag = q.optDouble("mag", 0), lat = q.optDouble("lat"), lon = q.optDouble("lon");
                if (id.isEmpty() || now - t > 2 * 3600_000L) continue;
                String coarse = "k" + (t / 300000L) + "_" + Math.round(lat) + "_" + Math.round(lon);
                if (seen.contains(id) || seen.contains(coarse)) continue;
                seen.add(id);
                seen.add(coarse);
                if (first) continue;
                int lvl = level(mag, lat, lon, ulat, ulon);
                boolean pan = inPanama(lat, lon);
                if (lvl >= 2 || (pan && mag >= 3.5)) {
                    if (sent < 3) { notify(ctx, base, id, q, lvl, km(ulat, ulon, lat, lon)); sent++; }
                }
            }
            if (seen.size() > 300) seen = new HashSet<>(new java.util.ArrayList<>(seen).subList(seen.size() - 200, seen.size()));
            sp.edit().putStringSet("seen", seen).putBoolean("init", true).apply();
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private static boolean inPanama(double lat, double lon) { return lat >= 4.5 && lat <= 10.5 && lon >= -84.0 && lon <= -76.5; }

    /** Misma lógica que levelFor() de la web. */
    private static int level(double m, double lat, double lon, double ulat, double ulon) {
        double d = km(ulat, ulon, lat, lon);
        boolean p = inPanama(lat, lon);
        if ((m >= 6 && d < 700) || (p && m >= 5 && d < 350)) return 3;
        if ((p && m >= 4) || (m >= 5.5 && d < 1200) || (m >= 5 && d < 300)) return 2;
        if ((p && m >= 2.5) || (m >= 4.5 && d < 800)) return 1;
        return 0;
    }

    private static double km(double la1, double lo1, double la2, double lo2) {
        double r = 6371, dLa = Math.toRadians(la2 - la1), dLo = Math.toRadians(lo2 - lo1);
        double h = Math.sin(dLa / 2) * Math.sin(dLa / 2) + Math.cos(Math.toRadians(la1)) * Math.cos(Math.toRadians(la2)) * Math.sin(dLo / 2) * Math.sin(dLo / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    private void notify(Context ctx, String base, String id, JSONObject q, int lvl, double dist) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        String ch = lvl >= 2 ? "sismos_fuertes" : "sismos_info";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(ch, lvl >= 2 ? "Alertas sísmicas" : "Sismos informativos", lvl >= 2 ? NotificationManager.IMPORTANCE_HIGH : NotificationManager.IMPORTANCE_DEFAULT);
            if (lvl >= 2) c.setVibrationPattern(new long[]{0, 500, 150, 500, 150, 800});
            nm.createNotificationChannel(c);
        }
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(base + "/?q=" + Uri.encode(id)));
        open.setPackage(ctx.getPackageName());
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(ctx, id.hashCode(), open, flags);
        String title = (lvl >= 3 ? "🔴 SISMO FUERTE · M" : lvl == 2 ? "🟠 ALERTA · M" : "🔵 Sismo M") + String.format(java.util.Locale.US, "%.1f", q.optDouble("mag"));
        String body = q.optString("place") + " · prof. " + Math.round(q.optDouble("depth", 0)) + " km · a " + Math.round(dist) + " km de ti";
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, ch)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(lvl >= 2 ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT);
        try { nm.notify(id.hashCode(), b.build()); } catch (SecurityException ignored) { }
    }
}
