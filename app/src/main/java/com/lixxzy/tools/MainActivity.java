package com.lixxzy.tools;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {

    private Switch switchTools;
    private boolean isToolsOn = false;
    
    // Nama folder di dalam APK
    private final String ASSET_FOLDER = "mod_files"; 
    
    // TARGET OTOMATIS
    private final String TARGET_TH = "/storage/emulated/0/Android/data/com.dts.freefireth/files/";
    private final String TARGET_MAX = "/storage/emulated/0/Android/data/com.dts.freefiremax/files/";
    
    private static final int SHIZUKU_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        switchTools = findViewById(R.id.switchTools);
        Button btnFF = findViewById(R.id.btnFF);
        Button btnFFMax = findViewById(R.id.btnFFMax);

        // Minta izin storage standar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }

        // Logika Toggle ON / OFF
        switchTools.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isToolsOn = isChecked;
            if (isChecked) {
                switchTools.setText("Lixxzy Tools : ON");
                switchTools.setTextColor(0xFF00FFCC);
            } else {
                switchTools.setText("Lixxzy Tools : OFF");
                switchTools.setTextColor(0xFFFF0033);
                if (checkShizuku()) {
                    removeFiles();
                }
            }
        });

        // Tombol FF TH
        btnFF.setOnClickListener(v -> {
            if (!isToolsOn) { showToast("Nyalakan Tools dulu!"); return; }
            if (!checkShizuku()) { requestShizuku(); return; }
            injectFiles("com.dts.freefireth", TARGET_TH);
        });

        // Tombol FF MAX
        btnFFMax.setOnClickListener(v -> {
            if (!isToolsOn) { showToast("Nyalakan Tools dulu!"); return; }
            if (!checkShizuku()) { requestShizuku(); return; }
            injectFiles("com.dts.freefiremax", TARGET_MAX);
        });
    }

    private void injectFiles(String pkgName, String targetDir) {
        showToast("Memproses file...");
        
        new Thread(() -> {
            try {
                // 1. Ekstrak dari dalam APK ke folder Cache Aplikasi dulu
                File cacheDir = new File(getExternalFilesDir(null), "mod_temp");
                if (!cacheDir.exists()) cacheDir.mkdirs();

                String[] files = getAssets().list(ASSET_FOLDER);
                if (files == null || files.length == 0) {
                    runOnUiThread(() -> showToast("File di dalam APK kosong!"));
                    return;
                }

                for (String file : files) {
                    InputStream in = getAssets().open(ASSET_FOLDER + "/" + file);
                    OutputStream out = new FileOutputStream(new File(cacheDir, file));
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    in.close();
                    out.close();
                }

                // 2. Tembak perintah Shell via Shizuku buat mindahin ke folder files/
                String cmd = "mkdir -p " + targetDir + " && cp -r " + cacheDir.getAbsolutePath() + "/* " + targetDir;
                executeShizukuCmd(cmd);

                // 3. Bersihkan Cache
                File[] tempFiles = cacheDir.listFiles();
                if (tempFiles != null) {
                    for (File f : tempFiles) f.delete();
                }
                cacheDir.delete();

                // 4. Buka Game
                runOnUiThread(() -> {
                    showToast("Tools Aktif! Membuka Game...");
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(pkgName);
                    if (launchIntent != null) startActivity(launchIntent);
                });

            } catch (Exception e) {
                runOnUiThread(() -> showToast("Gagal: " + e.getMessage()));
            }
        }).start();
    }

    private void removeFiles() {
        showToast("Menghapus file mod...");
        new Thread(() -> {
            try {
                String[] files = getAssets().list(ASSET_FOLDER);
                if (files != null && files.length > 0) {
                    StringBuilder cmd = new StringBuilder();
                    // Hapus HANYA file yang namanya persis sama dengan di dalam APK
                    for (String file : files) {
                        cmd.append("rm -f ").append(TARGET_TH).append(file).append("; ");
                        cmd.append("rm -f ").append(TARGET_MAX).append(file).append("; ");
                    }
                    executeShizukuCmd(cmd.toString());
                    runOnUiThread(() -> showToast("File Delete"));
                }
            } catch (Exception e) {
                runOnUiThread(() -> showToast("Gagal Hapus: " + e.getMessage()));
            }
        }).start();
    }

    // ==========================================
    // BAGIAN SHIZUKU
    // ==========================================
    private boolean checkShizuku() {
        if (Shizuku.isPreV11()) return false;
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
    }

    private void requestShizuku() {
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            showToast("Izin Shizuku ditolak permanen. Buka app Shizuku.");
        } else {
            Shizuku.requestPermission(SHIZUKU_CODE);
        }
    }

    private void executeShizukuCmd(String command) {
        try {
            Method method = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            method.setAccessible(true);
            Process process = (Process) method.invoke(null, new String[]{"sh", "-c", command}, null, null);
            process.waitFor(); 
        } catch (Exception e) {
            runOnUiThread(() -> showToast("Shizuku CMD Error: " + e.getMessage()));
        }
    }

    private final Shizuku.OnRequestPermissionResultListener REQUEST_PERMISSION_LISTENER = (requestCode, grantResult) -> {
        if (requestCode == SHIZUKU_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                showToast("Shizuku Diizinkan! Klik START lagi.");
            } else {
                showToast("Izin Shizuku ditolak!");
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_LISTENER);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_LISTENER);
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
            }
