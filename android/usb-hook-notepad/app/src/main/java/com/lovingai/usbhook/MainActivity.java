package com.lovingai.usbhook;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 极简记事：追加到应用外部专属目录下的文本文件。LovingAI 本体在 PC，由 adb pull 取回。
 */
public class MainActivity extends Activity {

    static final String NOTE_FILE = "lovingai_usb_notes.txt";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView head = new TextView(this);
        head.setText(
                "仅本地记事；生命体在 PC。\n通过 USB 调试由 LovingAI 拉取下方路径中的文件。");

        final EditText et = new EditText(this);
        et.setMinLines(5);
        et.setHint("写一句现场/天气/你想让 Ta 知道的事…");

        final File out = new File(getExternalFilesDir(null), NOTE_FILE);

        Button btn = new Button(this);
        btn.setText("追加到导出文件");
        btn.setOnClickListener(
                v -> {
                    String line = et.getText().toString().trim();
                    if (line.isEmpty()) {
                        Toast.makeText(this, "内容为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try (FileWriter fw = new FileWriter(out, true)) {
                        fw.write(System.currentTimeMillis());
                        fw.write('\t');
                        fw.write(line.replace('\n', ' ').replace('\r', ' '));
                        fw.write('\n');
                        et.setText("");
                        Toast.makeText(
                                        this,
                                        "已写入\n" + out.getAbsolutePath(),
                                        Toast.LENGTH_LONG)
                                .show();
                    } catch (IOException e) {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });

        TextView path = new TextView(this);
        path.setTextIsSelectable(true);
        path.setText("导出文件：\n" + out.getAbsolutePath());

        root.addView(head);
        root.addView(et);
        root.addView(btn);
        root.addView(path);
        setContentView(root);
    }
}
