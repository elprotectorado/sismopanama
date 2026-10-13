package pa.istmo.sismo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/** Pantalla de arranque: pide permisos una sola vez, programa las alertas y abre la app. */
public class PermissionActivity extends Activity {
    private static final int REQ_PERMS = 7, REQ_BATT = 8;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("istmo", MODE_PRIVATE);
        stepPermissions();
    }

    private void stepPermissions() {
        List<String> need = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!granted(Manifest.permission.ACCESS_COARSE_LOCATION)) need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (!need.isEmpty() && !sp.getBoolean("askedPerms", false)) {
            sp.edit().putBoolean("askedPerms", true).apply();
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), REQ_PERMS);
        } else {
            stepBattery();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        stepBattery();
    }

    private void stepBattery() {
        try {
            if (Build.VERSION.SDK_INT >= 23 && !sp.getBoolean("askedBatt", false)) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    sp.edit().putBoolean("askedBatt", true).apply();
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
                    startActivityForResult(i, REQ_BATT);
                    return;
                }
            }
        } catch (Exception ignored) { }
        go();
    }

    @Override
    protected void onActivityResult(int code, int res, Intent data) {
        super.onActivityResult(code, res, data);
        go();
    }

    private boolean granted(String p) {
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED;
    }

    private void go() {
        saveLocation();
        AlertWorker.schedule(this);
        Intent i = new Intent(this, com.google.androidbrowserhelper.trusted.LauncherActivity.class);
        if (getIntent() != null && getIntent().getData() != null) i.setData(getIntent().getData());
        startActivity(i);
        finish();
    }

    private void saveLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String p : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
                try {
                    Location l = lm.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (SecurityException ignored) { }
            }
            if (best != null) sp.edit().putFloat("lat", (float) best.getLatitude()).putFloat("lon", (float) best.getLongitude()).apply();
        } catch (Exception ignored) { }
    }
}
