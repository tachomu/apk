package com.mealcoach.app;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class LogStore {
    private static final String FILE = "meal_log.csv";

    private LogStore() {}

    public static synchronized void log(Context context, String event, int meals, int snacks, String note, String photoUri) {
        try {
            File file = new File(context.getFilesDir(), FILE);
            boolean fresh = !file.exists();
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
                if (fresh) {
                    writer.write("timestamp,event,meals,snacks,note,photo_uri\n");
                }
                String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
                writer.write(csv(ts) + "," + csv(event) + "," + meals + "," + snacks + "," + csv(note) + "," + csv(photoUri) + "\n");
            }
        } catch (Exception ignored) {
        }
    }

    private static String csv(String value) {
        if (value == null) value = "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public static String recent(Context context, int maxLines) {
        File file = new File(context.getFilesDir(), FILE);
        if (!file.exists()) return "Поки порожньо";
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) { first = false; continue; }
                lines.add(line);
            }
        } catch (Exception e) {
            return "Не вдалося прочитати журнал";
        }
        if (lines.isEmpty()) return "Поки порожньо";
        int start = Math.max(0, lines.size() - maxLines);
        List<String> tail = new ArrayList<>(lines.subList(start, lines.size()));
        Collections.reverse(tail);
        StringBuilder out = new StringBuilder();
        for (String raw : tail) {
            String[] p = raw.split(",", -1);
            if (p.length >= 2) {
                String ts = p[0].replace("\"", "");
                String ev = p[1].replace("\"", "");
                out.append(ts).append("  •  ").append(label(ev)).append("\n");
            }
        }
        return out.toString().trim();
    }

    private static String label(String event) {
        switch (event) {
            case "WAKE": return "Прокинувся";
            case "SIT_EAT": return "Сів їсти";
            case "FULL_MEAL": return "Поїв";
            case "SNACK": return "Перекус";
            case "DELAY_15": return "Відклав на 15 хв";
            case "DELAY_BLOCKED": return "Відкладання заблоковано";
            case "SLEEP": return "Ліг спати";
            case "PHOTO": return "Фото їжі";
            case "TEST_START": return "Запущено тест нагадувань";
            default: return event;
        }
    }

    public static boolean copyTo(Context context, Uri destination) {
        File file = new File(context.getFilesDir(), FILE);
        if (!file.exists()) return false;
        try (FileInputStream in = new FileInputStream(file);
             OutputStream out = context.getContentResolver().openOutputStream(destination, "w")) {
            if (out == null) return false;
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
