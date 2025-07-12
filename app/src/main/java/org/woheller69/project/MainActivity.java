package org.woheller69.project;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.Manifest;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private int[] counts;
    private List<TextView> countTextViews = new ArrayList<>();
    private TextView tvTotal;
    private ImageButton btnReset;
    private List<String> pricesList;
    private TextView tvPriceListDate;
    private SharedPreferences sharedPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestNotificationPermission();
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        String countsStr = sharedPref.getString("counts", null);

        PriceList priceList = new PriceList(this, countsStr==null);
        pricesList = priceList.loadPriceList(this);
        tvPriceListDate = findViewById(R.id.tvPriceListDate);
        tvPriceListDate.setText("Stand Preisliste:" + priceList.getPriceListDate(this));
        counts = new int[pricesList.size()];

        restoreCounts();

        LinearLayout itemsContainer = findViewById(R.id.itemsContainer);
        tvTotal = findViewById(R.id.tvTotal);
        btnReset = findViewById(R.id.btnReset);


        for (int i = 0; i < pricesList.size(); i++) {
            createItemRow(itemsContainer, i);
        }

        updateTotal();

        btnReset.setOnClickListener(v -> {
            HapticFeedback.vibrate(this);
            Snackbar.make(v, "ZURÜCKSETZEN?", Snackbar.LENGTH_LONG)
                    .setAction("OK", view -> {
                        // Reset counters in memory
                        for (int i = 0; i < counts.length; i++) {
                            counts[i] = 0;
                            countTextViews.get(i).setText("0");
                        }
                        sharedPref.edit().clear().apply();
                        dismissNotification();
                        HapticFeedback.vibrate(this);
                        updateTotal(); // Update the total display
                    })
                    .show();
        });
        scheduleDailyReminder(this);
    }

    private void createItemRow(LinearLayout container, int index) {
        // Inflate the item layout
        View rowView = getLayoutInflater().inflate(R.layout.item, container, false);
        LinearLayout row = (LinearLayout) rowView;

        // Find views in the inflated layout
        TextView tvName = row.findViewById(R.id.tvName);
        TextView tvPrice = row.findViewById(R.id.tvPrice);
        TextView tvCount = row.findViewById(R.id.tvCount);
        ImageButton btnMinus = row.findViewById(R.id.btnMinus);
        ImageButton btnPlus = row.findViewById(R.id.btnPlus);

        // Set item data
        tvName.setText(pricesList.get(index).split("\\|")[0]);
        tvPrice.setText(pricesList.get(index).split("\\|")[1] + " €");
        tvCount.setText(String.valueOf(counts[index]));

        // Add to the list for later updates
        countTextViews.add(tvCount);

        // Set button listeners
        btnMinus.setOnClickListener(v -> {
            if (counts[index] > 0) {
                counts[index]--;
                tvCount.setText(String.valueOf(counts[index]));
                saveCounts();
                HapticFeedback.vibrate(this);
                updateTotal();
            }
        });

        btnPlus.setOnClickListener(v -> {
            counts[index]++;
            tvCount.setText(String.valueOf(counts[index]));
            saveCounts();
            HapticFeedback.vibrate(this);
            updateTotal();
        });

        // Add the row to the container
        container.addView(row);
    }


    private void restoreCounts() {
        String countsStr = sharedPref.getString("counts", null);
        if (countsStr != null) {
            String[] parts = countsStr.split("%");
            for (int i = 0; i < parts.length && i < counts.length; i++) {
                counts[i] = Integer.parseInt(parts[i]);
            }
        }
    }

    private void saveCounts() {
        SharedPreferences.Editor editor = sharedPref.edit();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < counts.length; i++) {
            sb.append(counts[i]);
            if (i < counts.length - 1) {
                sb.append("%");
            }
        }
        editor.putString("counts", sb.toString());
        editor.apply();
    }

    private void updateTotal() {
        double total = 0.0;
        for (int i = 0; i < pricesList.size(); i++) {
            try {
                double price = Double.parseDouble(pricesList.get(i).split("\\|")[1]);
                total += price * counts[i];
            } catch (NumberFormatException e) {
                // Handle error
            }
        }
        tvTotal.setText(String.format("Total: %.2f €", total));
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putFloat("total", (float) total).apply();
    }
    public void github(View v){
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/woheller69/tc83bierdeckel")));
    }

    public static void scheduleDailyReminder(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build();

        long initialDelay = getInitialDelay();
        Log.d("Intitial delay", String.valueOf(initialDelay));
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(DailyNotificationWorker.class, 1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .addTag("daily_reminder")
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "DailyReminderWork",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
        );
    }

    private static long getInitialDelay() {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();

        target.add(Calendar.DAY_OF_MONTH, now.get(Calendar.HOUR_OF_DAY) >= 9 ? 1 : 0);
        target.set(Calendar.HOUR_OF_DAY, 9);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        return target.getTimeInMillis() - now.getTimeInMillis();
    }

    public void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS},123);
            }
        }
    }

    private void dismissNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancelAll();
        }
    }
}

