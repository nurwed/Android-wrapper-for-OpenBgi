package app.suba;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends SDLActivity {
    private static final String PREFS_NAME = "suba";
    private static final String KEY_GAME_DIR = "game_dir";
    private static final String DEFAULT_GAME_DIR = "/storage/emulated/0/BGI";
    private static final String EXTRA_PICK_FOLDER = "pick_folder";
    private static final int REQUEST_PICK_FOLDER = 1001;

    private static final String GAMES_JSON = "games.json";
    private static final String GAMES_JSON_CONTENT =
            "[ { \"match\": \"SubarashikihibiENG\", \"profile\": \"1.653\", \"product\": \"SubarashikihibiENG\" } ]";
    private static final String GAMES_JSON_PROMPT =
            "games.json is missing. Create it for Subarashiki Hibi (English)?";
    private static final String[] GAME_MARKER_FILES = {
            "data02100.arc", "data02101.arc", "data02980.arc", "data02989.arc", "data02994.arc"
    };
    private static final int GAME_MARKER_THRESHOLD = 3;

    private static boolean sForcePickerOnce = false;

    private SharedPreferences prefs;
    private String gameDir;
    private boolean pickerMode;
    private boolean forcePicker;
    private boolean createPromptVisible;

    private TextView statusView;
    private Button actionButton;
    private String pickerMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        forcePicker = getIntent().getBooleanExtra(EXTRA_PICK_FOLDER, false) || sForcePickerOnce;
        sForcePickerOnce = false;
        if (forcePicker) {
            getIntent().removeExtra(EXTRA_PICK_FOLDER);
        }

        String saved = prefs.getString(KEY_GAME_DIR, DEFAULT_GAME_DIR);
        gameDir = (saved == null || saved.trim().isEmpty()) ? DEFAULT_GAME_DIR : saved;

        File launchDir = new File(gameDir);
        String launchError = validateGameDir(launchDir);
        boolean canCreateOnLaunch = launchError == null && canCreateGamesJson(launchDir);

        boolean mustPickFolder = forcePicker || launchError != null;

        super.onCreate(savedInstanceState);

        if (mustPickFolder) {
            showPicker();
        } else if (canCreateOnLaunch) {
            // Keep the game surface detached until games.json exists, so the
            // engine cannot start against a folder that is missing it.
            showPicker(false);
            showCreateGamesJsonPrompt(launchDir);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!pickerMode) {
            return;
        }
        if (!forcePicker && hasStorageAccess()) {
            File dir = new File(gameDir);
            if (validateGameDir(dir) == null) {
                if (canCreateGamesJson(dir)) {
                    // Do not auto-start: the folder still needs a games.json.
                    showCreateGamesJsonPrompt(dir);
                } else {
                    startGame();
                }
                return;
            }
        }
        updatePickerUi(pickerMessage);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) {
            return;
        }
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_PICK_FOLDER, false)) {
            intent.removeExtra(EXTRA_PICK_FOLDER);
            if (pickerMode) {
                forcePicker = true;
                openSystemFolderPicker();
            } else {
                sForcePickerOnce = true;
                recreate();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_FOLDER) {
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            updatePickerUi("No folder selected.");
            return;
        }

        File dir = treeUriToDir(data.getData());
        if (dir == null) {
            updatePickerUi("Choose the folder");
            return;
        }

        String error = validateGameDir(dir);
        if (error != null) {
            updatePickerUi(dir.getAbsolutePath() + "\n" + error);
            return;
        }

        if (canCreateGamesJson(dir)) {
            showCreateGamesJsonPrompt(dir);
            return;
        }

        gameDir = dir.getAbsolutePath();
        prefs.edit().putString(KEY_GAME_DIR, gameDir).apply();
        startGame();
    }

    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL2", "main" };
    }

    @Override
    protected String[] getArguments() {
        return new String[] { gameDir != null ? gameDir : DEFAULT_GAME_DIR };
    }

    private void showPicker() {
        showPicker(true);
    }

    private void showPicker(boolean openFolderPicker) {
        pickerMode = true;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);

        TextView heading = new TextView(this);
        heading.setText("Select game folder");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(20);
        root.addView(heading);

        statusView = new TextView(this);
        statusView.setTextColor(Color.parseColor("#FFCC66"));
        statusView.setTextSize(14);
        statusView.setPadding(0, dp(12), 0, dp(16));
        root.addView(statusView);

        actionButton = new Button(this);
        actionButton.setOnClickListener(v -> {
            if (!hasStorageAccess()) {
                openStorageSettings();
            } else {
                openSystemFolderPicker();
            }
        });
        root.addView(actionButton);

        setContentView(root);
        updatePickerUi(null);

        if (openFolderPicker && hasStorageAccess()) {
            openSystemFolderPicker();
        }
    }

    private void updatePickerUi(String message) {
        if (statusView == null || actionButton == null) {
            return;
        }
        pickerMessage = message;
        if (!hasStorageAccess()) {
            statusView.setText("All files access is required so the game can read your folder.");
            actionButton.setText("Grant file access");
        } else {
            statusView.setText(message != null
                    ? message
                    : "Choose the folder with your .arc files.");
            actionButton.setText("Choose the right folder");
        }
    }

    private void startGame() {
        pickerMode = false;
        forcePicker = false;
        setContentView(mLayout);
    }

    private void openSystemFolderPicker() {
        if (!hasStorageAccess()) {
            openStorageSettings();
            return;
        }
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_PICK_FOLDER);
        } catch (Exception e) {
            updatePickerUi("Could not open system file manager.");
        }
    }

    private File treeUriToDir(Uri uri) {
        String docId = DocumentsContract.getTreeDocumentId(uri);
        if (docId == null) {
            return null;
        }
        if (docId.startsWith("raw:")) {
            return new File(docId.substring(4));
        }
        if (!"com.android.externalstorage.documents".equals(uri.getAuthority())) {
            return null;
        }

        String[] parts = docId.split(":", 2);
        String volume = parts[0];
        String subPath = parts.length > 1 ? parts[1] : "";

        if ("primary".equalsIgnoreCase(volume)) {
            File root = Environment.getExternalStorageDirectory();
            return subPath.isEmpty() ? root : new File(root, subPath);
        }
        if ("home".equalsIgnoreCase(volume)) {
            File root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            return subPath.isEmpty() ? root : new File(root, subPath);
        }
        if (!volume.isEmpty()) {
            File root = new File("/storage/" + volume);
            if (root.isDirectory()) {
                return subPath.isEmpty() ? root : new File(root, subPath);
            }
        }
        return null;
    }

    private String validateGameDir(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return "Folder does not exist.";
        }
        File[] files = dir.listFiles();
        if (!dir.canRead() || files == null) {
            return "Folder cannot be read.";
        }

        boolean hasArc = hasArcFile(files);
        boolean missingGamesJson = !hasGamesJson(dir);
        boolean recognisedGame = countGameMarkers(files) >= GAME_MARKER_THRESHOLD;


        if (missingGamesJson && hasArc && recognisedGame) {
            return null;
        }

        List<String> missing = new ArrayList<>();
        if (missingGamesJson) {
            missing.add(GAMES_JSON);
        }
        if (!hasArc) {
            missing.add("an .arc file");
        }

        if (missing.isEmpty()) {
            return null;
        }

        StringBuilder message = new StringBuilder("Missing: ");
        for (int i = 0; i < missing.size(); i++) {
            if (i > 0) {
                message.append(", ");
            }
            message.append(missing.get(i));
        }
        message.append(".");

        if (missingGamesJson && hasArc && !recognisedGame) {
            message.append(" The game in this folder is not recognised (found ")
                    .append(countGameMarkers(files))
                    .append(" of 5 expected .arc files; they must be directly inside this folder).");
        }
        return message.toString();
    }

    private boolean hasGamesJson(File dir) {
        return new File(dir, GAMES_JSON).isFile();
    }

    private boolean hasArcFile(File[] files) {
        if (files == null) {
            return false;
        }
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase(Locale.US).endsWith(".arc")) {
                return true;
            }
        }
        return false;
    }

    private int countGameMarkers(File[] files) {
        if (files == null) {
            return 0;
        }
        int count = 0;
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            String name = file.getName().toLowerCase(Locale.US);
            for (String marker : GAME_MARKER_FILES) {
                if (name.equals(marker)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private boolean canCreateGamesJson(File dir) {
        if (dir == null || !dir.isDirectory() || hasGamesJson(dir)) {
            return false;
        }
        File[] files = dir.listFiles();
        return hasArcFile(files) && countGameMarkers(files) >= GAME_MARKER_THRESHOLD;
    }

    private void showCreateGamesJsonPrompt(File dir) {
        if (createPromptVisible) {
            return;
        }
        createPromptVisible = true;
        new AlertDialog.Builder(this)
                .setMessage(GAMES_JSON_PROMPT)
                .setPositiveButton("Create", (dialog, which) -> {
                    createPromptVisible = false;
                    createGamesJsonAndContinue(dir);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    createPromptVisible = false;
                    cancelCreateGamesJson();
                })
                .setOnCancelListener(dialog -> {
                    createPromptVisible = false;
                    cancelCreateGamesJson();
                })
                .show();
    }

    private void cancelCreateGamesJson() {
        // Never save the folder on Cancel; stay in the picker so the user can
        // choose again.
        forcePicker = true;
        if (!pickerMode) {
            showPicker(false);
        }
        updatePickerUi("Folder not saved. Choose the folder containing games.json and your .arc files.");
    }

    private void createGamesJsonAndContinue(File dir) {
        String failure = createGamesJson(dir);
        if (failure != null) {
            forcePicker = true;
            if (!pickerMode) {
                showPicker(false);
            }
            updatePickerUi(dir.getAbsolutePath() + "\nCould not create games.json: " + failure + ".");
            return;
        }

        String error = validateGameDir(dir);
        if (error != null) {
            forcePicker = true;
            if (!pickerMode) {
                showPicker(false);
            }
            updatePickerUi(dir.getAbsolutePath() + "\n" + error);
            return;
        }

        gameDir = dir.getAbsolutePath();
        prefs.edit().putString(KEY_GAME_DIR, gameDir).apply();
        startGame();
    }
    private String createGamesJson(File dir) {
        File target = new File(dir, GAMES_JSON);
        if (target.exists()) {
            return null;
        }

        boolean created = false;
        FileOutputStream out = null;
        try {
            created = target.createNewFile();
            if (!created) {
                if (target.exists()) {
                    return null;
                }
                return dir.canWrite() ? "permission denied" : "the folder is read-only";
            }
            out = new FileOutputStream(target, false);
            out.write(GAMES_JSON_CONTENT.getBytes(StandardCharsets.UTF_8));
            out.flush();
            return null;
        } catch (SecurityException e) {
            deleteQuietly(target);
            return "permission denied";
        } catch (IOException e) {
            deleteQuietly(target);
            return describeWriteFailure(dir, e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private String describeWriteFailure(File dir, IOException e) {
        if (!dir.canWrite()) {
            return "the folder is read-only";
        }
        String detail = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.US);
        if (detail.contains("eacces") || detail.contains("permission denied")) {
            return "permission denied";
        }
        if (detail.contains("erofs") || detail.contains("read-only")) {
            return "the folder is read-only";
        }
        return "the file could not be written";
    }

    private void deleteQuietly(File file) {
        try {
            file.delete();
        } catch (SecurityException ignored) {
        }
    }

    private boolean hasStorageAccess() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
    }

    private void openStorageSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (Exception ignored) {
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
