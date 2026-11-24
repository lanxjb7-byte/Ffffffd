package com.lixxzy.tools;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private Switch switchTools;
    private boolean isToolsOn = false;
    private String pendingGame = ""; 
    
    // Folder di dalam APK tempat lo naruh file mod
    private final String ASSET_FOLDER = "mod_files"; 
    private SharedPreferences prefs;

    // Folder Picker (SAF) buat minta izin akses folder FF
    private final ActivityResultLauncher<Intent> folderPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri treeUri = result.getData().getData();
                    
                    // Simpan izin permanen biar nggak nanya lagi
                    getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    
                    // Simpan URI folder files/ ke memori
                    if (pendingGame.equals("TH")) {
                        prefs.edit().putString("uri_ffth", treeUri.toString()).apply();
                    } else {
                        prefs.edit().putString("uri_ffmax", treeUri.toString()).apply();
                    }
                    
                    // Eksekusi Inject File
                    injectFiles(pendingGame, treeUri);
                } else {
                    Toast.makeText(this, "Izin folder dibutuhkan untuk Inject!", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("LixxzyPrefs", MODE_PRIVATE);
        switchTools = findViewById(R.id.switchTools);
        Button btnFF = findViewById(R.id.btnFF);
        Button btnFFMax = findViewById(R.id.btnFFMax);

        switchTools.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isToolsOn = isChecked;
            if (isChecked) {
                switchTools.setText("Lixxzy Tools : ON");
                switchTools.setTextColor(0xFF00FFCC);
            } else {
                switchTools.setText("Lixxzy Tools : OFF");
                switchTools.setTextColor(0xFFFF0033);
                removeFiles();
            }
        });

        btnFF.setOnClickListener(v -> {
            if (!isToolsOn) {
                Toast.makeText(this, "Nyalakan Lixxzy Tools dulu!", Toast.LENGTH_SHORT).show();
                return;
            }
            pendingGame = "TH";
            checkAndStart("TH", "com.dts.freefireth");
        });

        btnFFMax.setOnClickListener(v -> {
            if (!isToolsOn) {
                Toast.makeText(this, "Nyalakan Lixxzy Tools dulu!", Toast.LENGTH_SHORT).show();
                return;
            }
            pendingGame = "MAX";
            checkAndStart("MAX", "com.dts.freefiremax");
        });
    }

    private void checkAndStart(String game, String pkgName) {
        String savedUri = prefs.getString(game.equals("TH") ? "uri_ffth" : "uri_ffmax", null);
        
        if (savedUri != null) {
            // Kalau udah pernah dikasih izin, langsung eksekusi
            injectFiles(game, Uri.parse(savedUri));
        } else {
            // Kalau belum, buka File Picker biar user nunjuk foldernya
            Toast.makeText(this, "Pilih folder 'files' di Android/data/" + pkgName + "/", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
            folderPicker.launch(intent);
        }
    }

    private void injectFiles(String game, Uri targetUri) {
        Toast.makeText(this, "Meng-ekstrak file...", Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            try {
                DocumentFile dir = DocumentFile.fromTreeUri(this, targetUri);
                String[] files = getAssets().list(ASSET_FOLDER);
                
                if (files == null || files.length == 0) {
                    runOnUiThread(() -> Toast.makeText(this, "File di dalam APK kosong!", Toast.LENGTH_LONG).show());
                    return;
                }

                for (String file : files) {
                    DocumentFile newFile = dir.createFile("application/octet-stream", file);
                    if (newFile != null) {
                        InputStream in = getAssets().open(ASSET_FOLDER + "/" + file);
                        OutputStream out = getContentResolver().openOutputStream(newFile.getUri());
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                        in.close();
                        out.close();
                    }
                }

                String pkg = game.equals("TH") ? "com.dts.freefireth" : "com.dts.freefiremax";
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Mod Aktif! Membuka Game...", Toast.LENGTH_SHORT).show();
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (launchIntent != null) startActivity(launchIntent);
                });

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Gagal: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void removeFiles() {
        Toast.makeText(this, "Menghapus file mod...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String[] files = getAssets().list(ASSET_FOLDER);
                
                // Hapus dari folder files/ FF TH
                String uriTH = prefs.getString("uri_ffth", null);
                if (uriTH != null && files != null) {
                    DocumentFile dir = DocumentFile.fromTreeUri(this, Uri.parse(uriTH));
                    if (dir != null) {
                        for (String file : files) {
                            DocumentFile f = dir.findFile(file);
                            if (f != null) f.delete();
                        }
                    }
                }

                // Hapus dari folder files/ FF MAX
                String uriMAX = prefs.getString("uri_ffmax", null);
                if (uriMAX != null && files != null) {
                    DocumentFile dir = DocumentFile.fromTreeUri(this, Uri.parse(uriMAX));
                    if (dir != null) {
                        for (String file : files) {
                            DocumentFile f = dir.findFile(file);
                            if (f != null) f.delete();
                        }
                    }
                }
                
                runOnUiThread(() -> Toast.makeText(this, "File Mod Dihapus, Game Kembali Normal", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Gagal Hapus: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
