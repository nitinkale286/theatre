package com.homecarcharge.mytrade;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    // UI Components
    private TextView tvTotalPnl, tvInTradingDays, tvInAddedOn, tvInProductDays, tvWinningsStruck;
    private TextView tvProfitableDaysCount, tvMonthYear, tvTransactionsHeader;
    private LinearLayout layoutMostProfitableDays, layoutTransactionList, layoutTodayDetails;
    private FloatingActionButton fabAdd;
    private ImageButton btnPrevMonth, btnNextMonth;

    // Data
    private List<Transaction> allTransactions = new ArrayList<>(); // All transactions across all months
    private List<Transaction> currentMonthTransactions = new ArrayList<>(); // Current month's transactions
    private Map<String, List<Transaction>> monthlyTransactions = new HashMap<>(); // Month-year -> Transactions
    private Map<Integer, Transaction> dailyTransactions = new HashMap<>(); // Day -> Transaction for current month

    // Current month tracking
    private Calendar currentCalendar;
    private int currentMonth;
    private int currentYear;
    private double totalPnl = 0;

    // SharedPreferences keys
    private static final String PREFS_NAME = "TraderDiaryPrefs";
    private static final String KEY_ALL_TRANSACTIONS = "all_transactions";
    private static final String KEY_MONTHLY_TRANSACTIONS = "monthly_transactions";

    // Image handling
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int MAX_IMAGES = 3;
    private List<String> selectedImagePaths = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupClickListeners();
        loadSavedData();
        initializeCurrentMonth();
        updateUIForCurrentMonth();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveData();
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveData();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK) {
            if (data != null) {
                if (data.getClipData() != null) {
                    // Multiple images selected
                    int count = Math.min(data.getClipData().getItemCount(), MAX_IMAGES - selectedImagePaths.size());
                    for (int i = 0; i < count; i++) {
                        Uri imageUri = data.getClipData().getItemAt(i).getUri();
                        String imagePath = getImagePath(imageUri);
                        if (imagePath != null) {
                            selectedImagePaths.add(imagePath);
                        }
                    }
                } else if (data.getData() != null) {
                    // Single image selected
                    Uri imageUri = data.getData();
                    String imagePath = getImagePath(imageUri);
                    if (imagePath != null && selectedImagePaths.size() < MAX_IMAGES) {
                        selectedImagePaths.add(imagePath);
                    }
                }
            }
        }
    }

    private void initializeViews() {
        tvTotalPnl = findViewById(R.id.tv_total_pnl);
        tvInTradingDays = findViewById(R.id.tv_trading_days);
        tvInAddedOn = findViewById(R.id.tv_added_on);
        tvInProductDays = findViewById(R.id.tv_product_days);
        tvWinningsStruck = findViewById(R.id.tv_winnings_struck);
        tvProfitableDaysCount = findViewById(R.id.tv_profitable_days_count);
        tvMonthYear = findViewById(R.id.tv_month_year);
        tvTransactionsHeader = findViewById(R.id.tv_transactions_header);
        layoutMostProfitableDays = findViewById(R.id.layout_most_profitable_days);
        layoutTransactionList = findViewById(R.id.layout_transaction_list);
        layoutTodayDetails = findViewById(R.id.layout_today_details);
        fabAdd = findViewById(R.id.fab_add);
        btnPrevMonth = findViewById(R.id.btn_prev_month);
        btnNextMonth = findViewById(R.id.btn_next_month);
    }

    private void initializeCurrentMonth() {
        currentCalendar = Calendar.getInstance();
        currentMonth = currentCalendar.get(Calendar.MONTH);
        currentYear = currentCalendar.get(Calendar.YEAR);

        // Update month-year display
        updateMonthYearDisplay();

        // Load current month's data
        loadCurrentMonthData();
    }

    private void updateMonthYearDisplay() {
        String monthName = new SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                .format(currentCalendar.getTime());
        tvMonthYear.setText(monthName);
        tvTransactionsHeader.setText(monthName);

        // Update "for Month Year" in P&L card
        TextView tvMonthLabel = findViewById(R.id.tv_month_label);
        tvMonthLabel.setText("for " + monthName);
    }

    private void loadCurrentMonthData() {
        String monthKey = getMonthKey(currentMonth, currentYear);

        // Get transactions for current month
        currentMonthTransactions = monthlyTransactions.get(monthKey);
        if (currentMonthTransactions == null) {
            currentMonthTransactions = new ArrayList<>();
            monthlyTransactions.put(monthKey, currentMonthTransactions);
        }

        // Build daily transactions map
        dailyTransactions.clear();
        for (Transaction t : currentMonthTransactions) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(t.getDate());
            int day = cal.get(Calendar.DAY_OF_MONTH);
            dailyTransactions.put(day, t);
        }

        // Calculate current month's P&L
        calculateCurrentMonthPnl();
    }

    private String getMonthKey(int month, int year) {
        return month + "-" + year;
    }

    private void calculateCurrentMonthPnl() {
        double totalProfit = 0;
        double totalLoss = 0;

        for (Transaction t : currentMonthTransactions) {
            if (t.isProfit()) {
                totalProfit += t.getAmount();
            } else {
                totalLoss += Math.abs(t.getAmount());
            }
        }

        totalPnl = totalProfit - totalLoss;
    }

    private void setupClickListeners() {
        fabAdd.setOnClickListener(v -> showAddTransactionDialog());

        btnPrevMonth.setOnClickListener(v -> navigateToPreviousMonth());
        btnNextMonth.setOnClickListener(v -> navigateToNextMonth());

        tvMonthYear.setOnClickListener(v -> showMonthSelectionDialog());

        // Set click listeners for calendar dates
        for (int day = 1; day <= 31; day++) {
            int resId = getResources().getIdentifier("date_" + String.format("%02d", day), "id", getPackageName());
            TextView dateView = findViewById(resId);
            if (dateView != null) {
                final int currentDay = day;
                dateView.setOnClickListener(v -> {
                    // Check if this is today's date
                    Calendar today = Calendar.getInstance();
                    if (currentCalendar.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                            currentCalendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                            currentDay == today.get(Calendar.DAY_OF_MONTH)) {
                        // Today's date clicked - show add dialog
                        showAddTransactionDialogForDate(today.getTime());
                    } else {
                        // Other date clicked - show transaction details if exists
                        Transaction t = dailyTransactions.get(currentDay);
                        if (t != null) {
                            showTransactionDetails(t);
                        } else {
                            Toast.makeText(MainActivity.this,
                                    "No transaction for " + currentDay + getDaySuffix(currentDay),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }
    }

    private String getDaySuffix(int day) {
        if (day >= 11 && day <= 13) {
            return "th";
        }
        switch (day % 10) {
            case 1: return "st";
            case 2: return "nd";
            case 3: return "rd";
            default: return "th";
        }
    }

    private void navigateToPreviousMonth() {
        currentCalendar.add(Calendar.MONTH, -1);
        currentMonth = currentCalendar.get(Calendar.MONTH);
        currentYear = currentCalendar.get(Calendar.YEAR);

        updateMonthYearDisplay();
        loadCurrentMonthData();
        updateUIForCurrentMonth();
    }

    private void navigateToNextMonth() {
        Calendar now = Calendar.getInstance();
        Calendar nextMonth = (Calendar) currentCalendar.clone();
        nextMonth.add(Calendar.MONTH, 1);

        // Check if next month is in the future
        if (nextMonth.after(now) && nextMonth.get(Calendar.MONTH) != now.get(Calendar.MONTH)) {
            // Ask user if they want to add transaction for future month
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Future Month");
            builder.setMessage("This is a future month. You can add transactions for planning, but they will be marked as future transactions.");
            builder.setPositiveButton("Continue", (dialog, which) -> {
                currentCalendar.add(Calendar.MONTH, 1);
                currentMonth = currentCalendar.get(Calendar.MONTH);
                currentYear = currentCalendar.get(Calendar.YEAR);

                updateMonthYearDisplay();
                loadCurrentMonthData();
                updateUIForCurrentMonth();
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        } else {
            currentCalendar.add(Calendar.MONTH, 1);
            currentMonth = currentCalendar.get(Calendar.MONTH);
            currentYear = currentCalendar.get(Calendar.YEAR);

            updateMonthYearDisplay();
            loadCurrentMonthData();
            updateUIForCurrentMonth();
        }
    }

    private void showMonthSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Month");

        // Get all months that have transactions
        List<String> monthsWithData = new ArrayList<>(monthlyTransactions.keySet());
        Collections.sort(monthsWithData, Collections.reverseOrder()); // Most recent first

        // Add current month if not already in list
        String currentMonthKey = getMonthKey(currentMonth, currentYear);
        if (!monthsWithData.contains(currentMonthKey)) {
            monthsWithData.add(currentMonthKey);
        }

        // Create month display names
        List<String> monthDisplayNames = new ArrayList<>();
        for (String monthKey : monthsWithData) {
            String[] parts = monthKey.split("-");
            int month = Integer.parseInt(parts[0]);
            int year = Integer.parseInt(parts[1]);

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.MONTH, month);
            cal.set(Calendar.YEAR, year);

            String monthName = new SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                    .format(cal.getTime());
            monthDisplayNames.add(monthName);
        }

        builder.setItems(monthDisplayNames.toArray(new String[0]), (dialog, which) -> {
            String selectedMonthKey = monthsWithData.get(which);
            String[] parts = selectedMonthKey.split("-");
            int month = Integer.parseInt(parts[0]);
            int year = Integer.parseInt(parts[1]);

            currentCalendar.set(Calendar.MONTH, month);
            currentCalendar.set(Calendar.YEAR, year);
            currentMonth = month;
            currentYear = year;

            updateMonthYearDisplay();
            loadCurrentMonthData();
            updateUIForCurrentMonth();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showTransactionDetails(Transaction transaction) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Transaction Details");

        // Create a scrollable view for details
        ScrollView scrollView = new ScrollView(this);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(16, 16, 16, 16);

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

        // Date
        addDetailRow(mainLayout, "Date:", sdf.format(transaction.getDate()));

        // Stock Name (if available)
        if (transaction.getStockName() != null && !transaction.getStockName().isEmpty()) {
            addDetailRow(mainLayout, "Stock:", transaction.getStockName());
        }

        // Amount
        addDetailRow(mainLayout, "Amount:", String.format("₹ %.2f", Math.abs(transaction.getAmount())));

        // Type
        addDetailRow(mainLayout, "Type:", transaction.isProfit() ? "Profit" : "Loss");

        // Capital Used (if available)
        if (transaction.getCapitalUsed() > 0) {
            addDetailRow(mainLayout, "Capital Used:", String.format("₹ %.2f", transaction.getCapitalUsed()));
        }

        // ROI (if available)
        if (transaction.getRoi() > 0) {
            addDetailRow(mainLayout, "ROI:", String.format("%.2f%%", transaction.getRoi()));
        }

        // Trade Entries (if available)
        if (transaction.getTradeEntries() != null && !transaction.getTradeEntries().isEmpty()) {
            addDetailRow(mainLayout, "Trade Entries:", "");
            for (int i = 0; i < transaction.getTradeEntries().size(); i++) {
                TradeEntry entry = transaction.getTradeEntries().get(i);
                addDetailRow(mainLayout,
                        String.format("  Entry %d:", i + 1),
                        String.format("Entry: ₹ %.2f, Exit: ₹ %.2f",
                                entry.getEntryPrice(), entry.getExitPrice()));
            }
        }

        // Reason (if available)
        if (transaction.getReason() != null && !transaction.getReason().isEmpty()) {
            addDetailRow(mainLayout, "Reason:", transaction.getReason());
        }

        // Images (if available)
        if (transaction.getImagePaths() != null && !transaction.getImagePaths().isEmpty()) {
            addDetailRow(mainLayout, "Images:", "");

            LinearLayout imageLayout = new LinearLayout(this);
            imageLayout.setOrientation(LinearLayout.HORIZONTAL);
            imageLayout.setPadding(0, 8, 0, 8);

            for (int i = 0; i < transaction.getImagePaths().size(); i++) {
                String imagePath = transaction.getImagePaths().get(i);

                ImageView imageView = new ImageView(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(30, 30);
                if (i > 0) params.leftMargin = 8;
                imageView.setLayoutParams(params);
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

                // Load image
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);

                    // Make image clickable
                    final int index = i;
                    imageView.setOnClickListener(v -> showFullScreenImage(imagePath));
                }

                imageLayout.addView(imageView);
            }

            mainLayout.addView(imageLayout);
        }

        scrollView.addView(mainLayout);
        builder.setView(scrollView);

        builder.setPositiveButton("OK", null);

        // Add delete button
        builder.setNegativeButton("Delete", (dialog, which) -> {
            deleteTransaction(transaction);
        });

        builder.show();
    }

    private void addDetailRow(LinearLayout layout, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        row.setPadding(0, 4, 0, 4);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(14);
        labelView.setTextColor(Color.DKGRAY);
        labelView.setTypeface(null, Typeface.BOLD);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.3f
        ));

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(14);
        valueView.setTextColor(Color.BLACK);
        valueView.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.7f
        ));

        row.addView(labelView);
        row.addView(valueView);
        layout.addView(row);
    }

    private void showFullScreenImage(String imagePath) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Create a custom layout with close button
        RelativeLayout layout = new RelativeLayout(this);

        ImageView imageView = new ImageView(this);
        imageView.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        ));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        }

        // Close button - Use standard Android close icon
        ImageButton closeButton = new ImageButton(this);
        closeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        closeButton.setBackgroundColor(Color.TRANSPARENT);

        RelativeLayout.LayoutParams closeParams = new RelativeLayout.LayoutParams(
                48, 48
        );
        closeParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        closeParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        closeParams.setMargins(0, 16, 16, 0);
        closeButton.setLayoutParams(closeParams);

        layout.addView(imageView);
        layout.addView(closeButton);

        builder.setView(layout);

        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));

        // Set click listener for close button
        closeButton.setOnClickListener(v -> dialog.dismiss());

        // Allow tapping anywhere to close
        imageView.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void deleteTransaction(Transaction transaction) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Transaction");
        builder.setMessage("Are you sure you want to delete this transaction?");

        builder.setPositiveButton("Delete", (dialog, which) -> {
            // Remove from all transactions list
            allTransactions.remove(transaction);

            // Remove from current month transactions
            currentMonthTransactions.remove(transaction);

            // Remove from dailyTransactions map
            Calendar cal = Calendar.getInstance();
            cal.setTime(transaction.getDate());
            int day = cal.get(Calendar.DAY_OF_MONTH);
            dailyTransactions.remove(day);

            // Update monthly transactions map
            String monthKey = getMonthKey(cal.get(Calendar.MONTH), cal.get(Calendar.YEAR));
            List<Transaction> monthTransactions = monthlyTransactions.get(monthKey);
            if (monthTransactions != null) {
                monthTransactions.remove(transaction);
                if (monthTransactions.isEmpty()) {
                    monthlyTransactions.remove(monthKey);
                }
            }

            // Update UI and save data
            updateUIForCurrentMonth();
            saveData();
            Toast.makeText(this, "Transaction deleted", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void saveData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // Save all transactions
        Gson gson = new Gson();
        String transactionsJson = gson.toJson(allTransactions);
        editor.putString(KEY_ALL_TRANSACTIONS, transactionsJson);

        // Save monthly transactions structure
        String monthlyTransactionsJson = gson.toJson(monthlyTransactions);
        editor.putString(KEY_MONTHLY_TRANSACTIONS, monthlyTransactionsJson);

        editor.apply();
    }

    private void loadSavedData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Gson gson = new Gson();

        // Load all transactions
        String transactionsJson = prefs.getString(KEY_ALL_TRANSACTIONS, null);
        if (transactionsJson != null) {
            Type transactionListType = new TypeToken<ArrayList<Transaction>>() {}.getType();
            List<Transaction> savedTransactions = gson.fromJson(transactionsJson, transactionListType);
            if (savedTransactions != null) {
                allTransactions = savedTransactions;
            }
        }

        // Load monthly transactions
        String monthlyTransactionsJson = prefs.getString(KEY_MONTHLY_TRANSACTIONS, null);
        if (monthlyTransactionsJson != null) {
            Type monthlyTransactionsType = new TypeToken<HashMap<String, List<Transaction>>>() {}.getType();
            Map<String, List<Transaction>> savedMonthlyTransactions = gson.fromJson(monthlyTransactionsJson, monthlyTransactionsType);
            if (savedMonthlyTransactions != null) {
                monthlyTransactions = savedMonthlyTransactions;
            }
        }

        // If no saved data, initialize with sample data for current month
        if (allTransactions.isEmpty()) {
            initializeSampleData();
        }
    }

    private void initializeSampleData() {
        Calendar cal = Calendar.getInstance();

        // Add sample transactions for current month
        cal.set(Calendar.DAY_OF_MONTH, 23);
        addTransactionToStructure(new Transaction(cal.getTime(), -5136.99, false));

        cal.set(Calendar.DAY_OF_MONTH, 22);
        addTransactionToStructure(new Transaction(cal.getTime(), -261.00, false));

        cal.set(Calendar.DAY_OF_MONTH, 19);
        addTransactionToStructure(new Transaction(cal.getTime(), 571.00, true));

        cal.set(Calendar.DAY_OF_MONTH, 18);
        addTransactionToStructure(new Transaction(cal.getTime(), 1072.00, true));

        cal.set(Calendar.DAY_OF_MONTH, 17);
        addTransactionToStructure(new Transaction(cal.getTime(), 1562.00, true));

        cal.set(Calendar.DAY_OF_MONTH, 15);
        addTransactionToStructure(new Transaction(cal.getTime(), 450.00, true));

        cal.set(Calendar.DAY_OF_MONTH, 14);
        addTransactionToStructure(new Transaction(cal.getTime(), -120.50, false));

        cal.set(Calendar.DAY_OF_MONTH, 10);
        addTransactionToStructure(new Transaction(cal.getTime(), 890.75, true));

        cal.set(Calendar.DAY_OF_MONTH, 8);
        addTransactionToStructure(new Transaction(cal.getTime(), 320.25, true));

        cal.set(Calendar.DAY_OF_MONTH, 5);
        addTransactionToStructure(new Transaction(cal.getTime(), -150.00, false));

        // Save the sample data
        saveData();
    }

    private void addTransactionToStructure(Transaction transaction) {
        allTransactions.add(transaction);

        Calendar cal = Calendar.getInstance();
        cal.setTime(transaction.getDate());
        int month = cal.get(Calendar.MONTH);
        int year = cal.get(Calendar.YEAR);
        String monthKey = getMonthKey(month, year);

        List<Transaction> monthTransactions = monthlyTransactions.get(monthKey);
        if (monthTransactions == null) {
            monthTransactions = new ArrayList<>();
            monthlyTransactions.put(monthKey, monthTransactions);
        }
        monthTransactions.add(transaction);
    }

    private void updateUIForCurrentMonth() {
        calculateAndDisplayStats();
        updateMostProfitableDays();
        updateCalendarColors();
        updateTransactionList();
        updateCalendarDates();
        updateTodayDetails();
    }

    private void updateCalendarDates() {
        // Get number of days in current month
        Calendar tempCal = (Calendar) currentCalendar.clone();
        tempCal.set(Calendar.DAY_OF_MONTH, 1);
        int daysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH);

        // Get first day of week (1 = Sunday, 2 = Monday, etc.)
        tempCal.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK);

        // Adjust for Monday as first day (Calendar.SUNDAY = 1)
        // We want Monday = 0, Tuesday = 1, etc.
        int startPosition = firstDayOfWeek - 2; // Subtract 2 to make Monday = 0
        if (startPosition < 0) startPosition = 6; // Sunday

        // Hide all date views first
        for (int day = 1; day <= 31; day++) {
            int resId = getResources().getIdentifier("date_" + String.format("%02d", day), "id", getPackageName());
            TextView dateView = findViewById(resId);
            if (dateView != null) {
                dateView.setVisibility(View.INVISIBLE);
            }
        }

        // Hide all empty views
        for (int i = 1; i <= 7; i++) {
            int resId = getResources().getIdentifier("empty_day" + i, "id", getPackageName());
            View emptyView = findViewById(resId);
            if (emptyView != null) {
                emptyView.setVisibility(View.INVISIBLE);
            }
        }

        // Show dates for current month at correct positions
        for (int day = 1; day <= daysInMonth; day++) {
            int position = startPosition + (day - 1);
            int week = position / 7;
            int dayInWeek = position % 7;

            // Get the correct week layout
            LinearLayout weekLayout = null;
            switch (week) {
                case 0: weekLayout = findViewById(R.id.layout_week1); break;
                case 1: weekLayout = findViewById(R.id.layout_week2); break;
                case 2: weekLayout = findViewById(R.id.layout_week3); break;
                case 3: weekLayout = findViewById(R.id.layout_week4); break;
                case 4: weekLayout = findViewById(R.id.layout_week5); break;
            }

            if (weekLayout != null) {
                // Find the TextView at the correct position in the week layout
                if (dayInWeek < weekLayout.getChildCount()) {
                    TextView dateView = (TextView) weekLayout.getChildAt(dayInWeek);
                    dateView.setVisibility(View.VISIBLE);
                    dateView.setText(String.valueOf(day));

                    // Highlight today's date
                    Calendar today = Calendar.getInstance();
                    if (currentCalendar.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                            currentCalendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                            day == today.get(Calendar.DAY_OF_MONTH)) {
                        dateView.setBackgroundColor(Color.parseColor("#E3F2FD")); // Light blue for today
                        dateView.setTextColor(Color.parseColor("#1976D2"));
                        dateView.setTypeface(null, Typeface.BOLD);
                    } else {
                        // Set background based on transaction
                        Transaction t = dailyTransactions.get(day);
                        if (t != null) {
                            if (t.isProfit()) {
                                dateView.setBackgroundColor(Color.parseColor("#E8F5E8"));
                                dateView.setTextColor(Color.parseColor("#4CAF50"));
                            } else {
                                dateView.setBackgroundColor(Color.parseColor("#FFEBEE"));
                                dateView.setTextColor(Color.parseColor("#F44336"));
                            }
                        } else {
                            dateView.setBackgroundColor(Color.WHITE);
                            dateView.setTextColor(Color.BLACK);
                        }
                    }
                }
            }
        }
    }

    private void calculateAndDisplayStats() {
        // 1. Calculate total P&L for current month
        double totalProfit = 0;
        double totalLoss = 0;

        for (Transaction t : currentMonthTransactions) {
            if (t.isProfit()) {
                totalProfit += t.getAmount();
            } else {
                totalLoss += Math.abs(t.getAmount());
            }
        }

        totalPnl = totalProfit - totalLoss;
        tvTotalPnl.setText(String.format("₹ %.2f", totalPnl));
        tvTotalPnl.setTextColor(totalPnl >= 0 ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));

        // 2. Calculate trading days stats for current month
        Calendar tempCal = (Calendar) currentCalendar.clone();
        tempCal.set(Calendar.DAY_OF_MONTH, 1);
        int totalDaysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH);

        int tradedDays = dailyTransactions.size();
        int profitableDays = 0;
        int winningStreak = 0;
        int currentStreak = 0;

        // Calculate profitable days and winning streak
        for (int day = 1; day <= totalDaysInMonth; day++) {
            Transaction t = dailyTransactions.get(day);
            if (t != null) {
                if (t.isProfit()) {
                    profitableDays++;
                    currentStreak++;
                    winningStreak = Math.max(winningStreak, currentStreak);
                } else {
                    currentStreak = 0;
                }
            }
        }

        // 3. Update stats display
        tvInTradingDays.setText(String.valueOf(totalDaysInMonth));
        tvInAddedOn.setText(String.valueOf(tradedDays));
        tvInProductDays.setText(String.valueOf(profitableDays));
        tvWinningsStruck.setText(String.valueOf(winningStreak));

        // 4. Update profitable days summary
        tvProfitableDaysCount.setText(String.format("%d/%d Traded Days", profitableDays, tradedDays));
    }

    private void updateMostProfitableDays() {
        layoutMostProfitableDays.removeAllViews();

        // Sort transactions by profit amount (highest first)
        List<Transaction> profitableTransactions = new ArrayList<>();
        for (Transaction t : currentMonthTransactions) {
            if (t.isProfit()) {
                profitableTransactions.add(t);
            }
        }

        Collections.sort(profitableTransactions, (t1, t2) ->
                Double.compare(Math.abs(t2.getAmount()), Math.abs(t1.getAmount())));

        // Display top 2 most profitable days
        int count = Math.min(2, profitableTransactions.size());
        for (int i = 0; i < count; i++) {
            Transaction t = profitableTransactions.get(i);
            addMostProfitableDayView(t, i == 0);
        }
    }

    private void addMostProfitableDayView(Transaction transaction, boolean isFirst) {
        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setBackgroundResource(R.drawable.transaction_item_background);
        itemLayout.setPadding(12, 12, 12, 12);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = isFirst ? 8 : 0;
        itemLayout.setLayoutParams(params);

        // Emoji indicator
        TextView emojiView = new TextView(this);
        emojiView.setText(isFirst ? "🔴" : "⚪");
        emojiView.setTextSize(12);
        emojiView.setGravity(android.view.Gravity.CENTER);
        emojiView.setWidth(24);
        itemLayout.addView(emojiView);

        // Date and amount
        TextView textView = new TextView(this);
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        String dateStr = sdf.format(transaction.getDate());
        textView.setText(String.format("%s: ₹ %.2f", dateStr, transaction.getAmount()));
        textView.setTextSize(14);
        textView.setTextColor(Color.BLACK);
        textView.setPadding(8, 0, 0, 0);
        itemLayout.addView(textView);

        layoutMostProfitableDays.addView(itemLayout);
    }

    private void updateCalendarColors() {
        // This is now handled in updateCalendarDates()
    }

    private void updateTransactionList() {
        layoutTransactionList.removeAllViews();

        // Sort transactions by date (newest first)
        Collections.sort(currentMonthTransactions, (t1, t2) ->
                t2.getDate().compareTo(t1.getDate()));

        // Display all transactions for current month
        for (int i = 0; i < currentMonthTransactions.size(); i++) {
            Transaction t = currentMonthTransactions.get(i);
            addTransactionView(t, i == currentMonthTransactions.size() - 1);
        }
    }

    private void addTransactionView(Transaction transaction, boolean isLast) {
        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setBackgroundResource(R.drawable.transaction_item_background);
        itemLayout.setPadding(16, 16, 16, 16);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = isLast ? 0 : 8;
        itemLayout.setLayoutParams(params);

        // Date
        TextView dateView = new TextView(this);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        dateView.setText(String.format("%s:", sdf.format(transaction.getDate())));
        dateView.setTextSize(14);
        dateView.setTextColor(Color.BLACK);
        dateView.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        itemLayout.addView(dateView);

        // Amount with sign
        TextView amountView = new TextView(this);
        String amountText = String.format("₹ %.2f", Math.abs(transaction.getAmount()));
        if (transaction.isProfit()) {
            amountText = "+" + amountText;
            amountView.setTextColor(Color.parseColor("#4CAF50"));
        } else {
            amountText = "-" + amountText;
            amountView.setTextColor(Color.parseColor("#F44336"));
        }
        amountView.setText(amountText);
        amountView.setTextSize(14);
        amountView.setTypeface(amountView.getTypeface(), android.graphics.Typeface.BOLD);
        itemLayout.addView(amountView);

        layoutTransactionList.addView(itemLayout);
    }

    private void updateTodayDetails() {
        layoutTodayDetails.removeAllViews();
        TextView tvTodayDetailsLabel = findViewById(R.id.tv_today_details_label);

        // Check if today has transactions
        Calendar today = Calendar.getInstance();
        int todayDay = today.get(Calendar.DAY_OF_MONTH);
        int todayMonth = today.get(Calendar.MONTH);
        int todayYear = today.get(Calendar.YEAR);

        if (currentMonth == todayMonth && currentYear == todayYear) {
            List<Transaction> todayTransactions = new ArrayList<>();
            for (Transaction t : currentMonthTransactions) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(t.getDate());
                if (cal.get(Calendar.DAY_OF_MONTH) == todayDay) {
                    todayTransactions.add(t);
                }
            }

            if (!todayTransactions.isEmpty()) {
                tvTodayDetailsLabel.setVisibility(View.VISIBLE);
                layoutTodayDetails.setVisibility(View.VISIBLE);

                for (Transaction t : todayTransactions) {
                    addTodayDetailView(t);
                }
            } else {
                tvTodayDetailsLabel.setVisibility(View.GONE);
                layoutTodayDetails.setVisibility(View.GONE);
            }
        } else {
            tvTodayDetailsLabel.setVisibility(View.GONE);
            layoutTodayDetails.setVisibility(View.GONE);
        }
    }

    private void addTodayDetailView(Transaction transaction) {
        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.VERTICAL);
        itemLayout.setPadding(12, 12, 12, 12);
        itemLayout.setBackgroundResource(R.drawable.transaction_item_background);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = 8;
        itemLayout.setLayoutParams(params);

        // Stock name if available
        if (transaction.getStockName() != null && !transaction.getStockName().isEmpty()) {
            TextView stockView = new TextView(this);
            stockView.setText("Stock: " + transaction.getStockName());
            stockView.setTextSize(14);
            stockView.setTextColor(Color.BLACK);
            stockView.setTypeface(null, Typeface.BOLD);
            itemLayout.addView(stockView);
        }

        // Amount
        TextView amountView = new TextView(this);
        String amountText = String.format("Amount: ₹ %.2f", Math.abs(transaction.getAmount()));
        if (transaction.isProfit()) {
            amountText = "Profit: " + amountText;
            amountView.setTextColor(Color.parseColor("#4CAF50"));
        } else {
            amountText = "Loss: " + amountText;
            amountView.setTextColor(Color.parseColor("#F44336"));
        }
        amountView.setText(amountText);
        amountView.setTextSize(14);
        itemLayout.addView(amountView);

        // ROI if available
        if (transaction.getRoi() > 0) {
            TextView roiView = new TextView(this);
            roiView.setText(String.format("ROI: %.2f%%", transaction.getRoi()));
            roiView.setTextSize(12);
            roiView.setTextColor(Color.parseColor("#666666"));
            itemLayout.addView(roiView);
        }

        layoutTodayDetails.addView(itemLayout);
    }

    private void showAddTransactionDialog() {
        showAddTransactionDialogForDate(new Date());
    }

    private void showAddTransactionDialogForDate(Date date) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add New Transaction");

        // Inflate custom dialog layout
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_transaction, null);
        builder.setView(dialogView);

        // Clear previous selections
        selectedImagePaths.clear();

        EditText etAmount = dialogView.findViewById(R.id.et_amount);
        EditText etStockName = dialogView.findViewById(R.id.et_stock_name);
        EditText etCapitalUsed = dialogView.findViewById(R.id.et_capital_used);
        EditText etRoi = dialogView.findViewById(R.id.et_roi);
        EditText etReason = dialogView.findViewById(R.id.et_reason);
        RadioGroup rgType = dialogView.findViewById(R.id.rg_type);
        RadioButton rbProfit = dialogView.findViewById(R.id.rb_profit);
        RadioButton rbLoss = dialogView.findViewById(R.id.rb_loss);
        LinearLayout layoutTradeEntries = dialogView.findViewById(R.id.layout_trade_entries);
        Button btnAddTradeEntry = dialogView.findViewById(R.id.btn_add_trade_entry);
        LinearLayout layoutImagePreviews = dialogView.findViewById(R.id.layout_image_previews);
        Button btnAddImage = dialogView.findViewById(R.id.btn_add_image);
        TextView tvDate = dialogView.findViewById(R.id.tv_date);
        Button btnSelectDate = dialogView.findViewById(R.id.btn_select_date);

        // Set date
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        tvDate.setText(sdf.format(date));

        btnSelectDate.setOnClickListener(v -> showDatePickerDialog(tvDate));

        // Track trade entries
        final int[] tradeEntryCount = {1};
        final List<View> tradeEntryViews = new ArrayList<>();

        // Add first trade entry
        LinearLayout firstEntry = dialogView.findViewById(R.id.trade_entry_1);
        tradeEntryViews.add(firstEntry);

        btnAddTradeEntry.setOnClickListener(v -> {
            if (tradeEntryCount[0] < 5) { // Limit to 5 trade entries
                tradeEntryCount[0]++;

                LinearLayout newTradeEntry = new LinearLayout(this);
                newTradeEntry.setOrientation(LinearLayout.HORIZONTAL);
                newTradeEntry.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                ));
                newTradeEntry.setPadding(0, 0, 0, 8);

                EditText etEntry = new EditText(this);
                etEntry.setId(View.generateViewId());
                LinearLayout.LayoutParams entryParams = new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                );
                entryParams.setMargins(0, 0, 8, 0);
                etEntry.setLayoutParams(entryParams);
                etEntry.setHint("Entry Price " + tradeEntryCount[0]);
                etEntry.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                etEntry.setPadding(8, 8, 8, 8);
                etEntry.setBackgroundResource(R.drawable.edittext_background);
                etEntry.setTextSize(14);

                EditText etExit = new EditText(this);
                etExit.setId(View.generateViewId());
                etExit.setLayoutParams(new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                ));
                etExit.setHint("Exit Price " + tradeEntryCount[0]);
                etExit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                etExit.setPadding(8, 8, 8, 8);
                etExit.setBackgroundResource(R.drawable.edittext_background);
                etExit.setTextSize(14);

                newTradeEntry.addView(etEntry);
                newTradeEntry.addView(etExit);
                layoutTradeEntries.addView(newTradeEntry);
                tradeEntryViews.add(newTradeEntry);
            } else {
                Toast.makeText(this, "Maximum 5 trade entries allowed", Toast.LENGTH_SHORT).show();
            }
        });

        // Image previews container
        LinearLayout layoutImageContainer = new LinearLayout(this);
        layoutImageContainer.setOrientation(LinearLayout.HORIZONTAL);
        layoutImageContainer.setGravity(Gravity.CENTER);
        layoutImagePreviews.addView(layoutImageContainer);

        btnAddImage.setOnClickListener(v -> {
            if (selectedImagePaths.size() < MAX_IMAGES) {
                openImagePicker(layoutImageContainer);
            } else {
                Toast.makeText(this, "Maximum 3 images allowed", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setPositiveButton("Add", (dialog, which) -> {
            handleAddTransaction(etAmount, etStockName, etCapitalUsed,
                    etRoi, etReason, rgType, tvDate, tradeEntryViews);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            clearTemporaryImages();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void openImagePicker(LinearLayout layoutImageContainer) {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update image previews when returning from image picker
        updateImagePreviewsInDialog();
    }

    private void updateImagePreviewsInDialog() {
        // Find the dialog container
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_transaction, null);
        LinearLayout layoutImagePreviews = dialogView.findViewById(R.id.layout_image_previews);

        if (layoutImagePreviews != null) {
            layoutImagePreviews.removeAllViews();

            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.HORIZONTAL);
            container.setGravity(Gravity.CENTER);
            layoutImagePreviews.addView(container);

            for (int i = 0; i < selectedImagePaths.size(); i++) {
                String imagePath = selectedImagePaths.get(i);
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                if (bitmap != null) {
                    // Create image view with remove button
                    RelativeLayout imageWrapper = new RelativeLayout(this);
                    LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(80, 80);
                    wrapperParams.setMargins(4, 4, 4, 4);
                    imageWrapper.setLayoutParams(wrapperParams);

                    ImageView imageView = new ImageView(this);
                    RelativeLayout.LayoutParams imageParams = new RelativeLayout.LayoutParams(70, 70);
                    imageParams.addRule(RelativeLayout.CENTER_IN_PARENT);
                    imageView.setLayoutParams(imageParams);
                    imageView.setImageBitmap(bitmap);
                    imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    imageView.setBackgroundResource(R.drawable.image_selector_background);

                    // Remove button - Using Material Design close icon
                    ImageButton removeButton = new ImageButton(this);
                    // Use vector drawable for close icon (Android default)
                    removeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                    removeButton.setBackgroundResource(R.drawable.remove_image_background);
                    removeButton.setPadding(4, 4, 4, 4);

                    RelativeLayout.LayoutParams removeParams = new RelativeLayout.LayoutParams(24, 24);
                    removeParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                    removeParams.addRule(RelativeLayout.ALIGN_PARENT_END);
                    removeButton.setLayoutParams(removeParams);

                    final int index = i;
                    removeButton.setOnClickListener(v -> {
                        selectedImagePaths.remove(index);
                        updateImagePreviewsInDialog();
                    });

                    imageWrapper.addView(imageView);
                    imageWrapper.addView(removeButton);
                    container.addView(imageWrapper);
                }
            }

            // Add placeholder if no images
            if (selectedImagePaths.isEmpty()) {
                TextView placeholder = new TextView(this);
                placeholder.setText("No images selected");
                placeholder.setTextSize(12);
                placeholder.setTextColor(Color.parseColor("#666666"));
                placeholder.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                placeholder.setLayoutParams(params);
                container.addView(placeholder);
            }
        }
    }

    private String getImagePath(Uri uri) {
        String[] projection = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            String path = cursor.getString(column_index);
            cursor.close();
            return path;
        }
        return uri.getPath();
    }

    private void clearTemporaryImages() {
        selectedImagePaths.clear();
    }

    private void handleAddTransaction(EditText etAmount, EditText etStockName, EditText etCapitalUsed,
                                      EditText etRoi, EditText etReason, RadioGroup rgType,
                                      TextView tvDate, List<View> tradeEntryViews) {
        String amountStr = etAmount.getText().toString().trim();
        if (amountStr.isEmpty()) {
            Toast.makeText(MainActivity.this, "Please enter amount", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr);
            boolean isProfit = rgType.getCheckedRadioButtonId() == R.id.rb_profit;

            // Get optional fields
            String stockName = etStockName.getText().toString().trim();
            String capitalUsedStr = etCapitalUsed.getText().toString().trim();
            String roiStr = etRoi.getText().toString().trim();
            String reason = etReason.getText().toString().trim();

            double capitalUsed = 0;
            if (!capitalUsedStr.isEmpty()) {
                capitalUsed = Double.parseDouble(capitalUsedStr);
            }

            double roi = 0;
            if (!roiStr.isEmpty()) {
                roi = Double.parseDouble(roiStr);
            }

            // Get trade entries
            List<TradeEntry> tradeEntries = new ArrayList<>();
            for (View tradeEntryView : tradeEntryViews) {
                if (tradeEntryView instanceof LinearLayout) {
                    LinearLayout entryLayout = (LinearLayout) tradeEntryView;
                    if (entryLayout.getChildCount() >= 2) {
                        EditText etEntry = (EditText) entryLayout.getChildAt(0);
                        EditText etExit = (EditText) entryLayout.getChildAt(1);

                        String entryStr = etEntry.getText().toString().trim();
                        String exitStr = etExit.getText().toString().trim();

                        if (!entryStr.isEmpty() && !exitStr.isEmpty()) {
                            try {
                                double entryPrice = Double.parseDouble(entryStr);
                                double exitPrice = Double.parseDouble(exitStr);
                                tradeEntries.add(new TradeEntry(entryPrice, exitPrice));
                            } catch (NumberFormatException e) {
                                // Skip invalid entries
                            }
                        }
                    }
                }
            }

            // Parse selected date
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            Date transactionDate;
            try {
                transactionDate = sdf.parse(tvDate.getText().toString());
            } catch (Exception e) {
                transactionDate = new Date();
            }

            // Get day, month, year from date
            Calendar cal = Calendar.getInstance();
            cal.setTime(transactionDate);
            int day = cal.get(Calendar.DAY_OF_MONTH);
            int month = cal.get(Calendar.MONTH);
            int year = cal.get(Calendar.YEAR);

            // Store values in final variables for use in lambdas
            final Date finalTransactionDate = transactionDate;
            final double finalAmount = amount;
            final boolean finalIsProfit = isProfit;
            final int finalDay = day;
            final int finalMonth = month;
            final int finalYear = year;
            final String finalStockName = stockName;
            final double finalCapitalUsed = capitalUsed;
            final double finalRoi = roi;
            final String finalReason = reason;
            final List<TradeEntry> finalTradeEntries = tradeEntries;
            final List<String> finalImagePaths = new ArrayList<>(selectedImagePaths);

            // Check if transaction is for current month
            if (month == currentMonth && year == currentYear) {
                // Check if transaction already exists for this day
                if (dailyTransactions.containsKey(day)) {
                    AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(this);
                    confirmBuilder.setTitle("Transaction Exists");
                    confirmBuilder.setMessage("A transaction already exists for this day. Do you want to replace it?");

                    confirmBuilder.setPositiveButton("Replace", (dialog1, which1) -> {
                        // Remove existing transaction
                        Transaction existing = dailyTransactions.get(finalDay);
                        if (existing != null) {
                            removeTransactionFromStructure(existing);
                        }

                        // Add new transaction using final variables
                        addNewTransaction(finalTransactionDate, finalAmount, finalIsProfit,
                                finalDay, finalMonth, finalYear, finalStockName,
                                finalReason, finalRoi, finalCapitalUsed,
                                finalTradeEntries, finalImagePaths);
                    });

                    confirmBuilder.setNegativeButton("Cancel", null);
                    confirmBuilder.show();
                } else {
                    // Add new transaction
                    addNewTransaction(finalTransactionDate, finalAmount, finalIsProfit,
                            finalDay, finalMonth, finalYear, finalStockName,
                            finalReason, finalRoi, finalCapitalUsed,
                            finalTradeEntries, finalImagePaths);
                }
            } else {
                // Transaction is for a different month
                AlertDialog.Builder monthBuilder = new AlertDialog.Builder(this);
                monthBuilder.setTitle("Different Month");
                monthBuilder.setMessage("This transaction is for " +
                        new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(transactionDate) +
                        ". Do you want to add it and switch to that month?");

                monthBuilder.setPositiveButton("Add and Switch", (dialog1, which1) -> {
                    // Add new transaction using final variables
                    addNewTransaction(finalTransactionDate, finalAmount, finalIsProfit,
                            finalDay, finalMonth, finalYear, finalStockName,
                            finalReason, finalRoi, finalCapitalUsed,
                            finalTradeEntries, finalImagePaths);

                    // Switch to that month
                    currentCalendar.set(Calendar.MONTH, finalMonth);
                    currentCalendar.set(Calendar.YEAR, finalYear);
                    currentMonth = finalMonth;
                    currentYear = finalYear;

                    updateMonthYearDisplay();
                    loadCurrentMonthData();
                    updateUIForCurrentMonth();
                });

                monthBuilder.setNegativeButton("Add Only", (dialog1, which1) -> {
                    // Add new transaction using final variables
                    addNewTransaction(finalTransactionDate, finalAmount, finalIsProfit,
                            finalDay, finalMonth, finalYear, finalStockName,
                            finalReason, finalRoi, finalCapitalUsed,
                            finalTradeEntries, finalImagePaths);
                });

                monthBuilder.setNeutralButton("Cancel", null);
                monthBuilder.show();
            }

        } catch (NumberFormatException e) {
            Toast.makeText(MainActivity.this, "Invalid amount", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDatePickerDialog(TextView tvDate) {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        android.app.DatePickerDialog datePickerDialog = new android.app.DatePickerDialog(
                this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    Calendar selectedCal = Calendar.getInstance();
                    selectedCal.set(selectedYear, selectedMonth, selectedDay);

                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                    tvDate.setText(sdf.format(selectedCal.getTime()));
                },
                year, month, day
        );

        datePickerDialog.show();
    }

    private void addNewTransaction(Date date, double amount, boolean isProfit, int day, int month, int year,
                                   String stockName, String reason, double roi, double capitalUsed,
                                   List<TradeEntry> tradeEntries, List<String> imagePaths) {
        // Create transaction with all fields
        Transaction newTransaction = new Transaction(date,
                isProfit ? Math.abs(amount) : -Math.abs(amount), isProfit, stockName, reason,
                roi, capitalUsed, tradeEntries, imagePaths);

        // Add to structure
        addTransactionToStructure(newTransaction);

        // If transaction is for current month, update current month data
        if (month == currentMonth && year == currentYear) {
            currentMonthTransactions.add(newTransaction);
            dailyTransactions.put(day, newTransaction);
        }

        // Update UI and save data
        updateUIForCurrentMonth();
        saveData();

        Toast.makeText(this, "Transaction added", Toast.LENGTH_SHORT).show();
    }

    private void removeTransactionFromStructure(Transaction transaction) {
        allTransactions.remove(transaction);

        Calendar cal = Calendar.getInstance();
        cal.setTime(transaction.getDate());
        int month = cal.get(Calendar.MONTH);
        int year = cal.get(Calendar.YEAR);
        String monthKey = getMonthKey(month, year);

        List<Transaction> monthTransactions = monthlyTransactions.get(monthKey);
        if (monthTransactions != null) {
            monthTransactions.remove(transaction);
            if (monthTransactions.isEmpty()) {
                monthlyTransactions.remove(monthKey);
            }
        }
    }
}