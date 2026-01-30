package com.homecarcharge.mytrade;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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
    private TextView tvProfitableDaysCount, tvMonthYear;
    private LinearLayout layoutMostProfitableDays, layoutTransactionList;
    private FloatingActionButton fabAdd;
    private ImageButton btnPrevMonth, btnNextMonth;

    // Calendar Views
    private LinearLayout[] weekLayouts = new LinearLayout[6];

    // Data
    private List<Transaction> allTransactions = new ArrayList<>();
    private List<Transaction> currentMonthTransactions = new ArrayList<>();
    private Map<String, List<Transaction>> monthlyTransactions = new HashMap<>();
    private Map<Integer, Transaction> dailyTransactions = new HashMap<>();

    // Current month tracking
    private Calendar currentCalendar;
    private int currentMonth;
    private int currentYear;
    private double totalPnl = 0;

    // SharedPreferences keys
    private static final String PREFS_NAME = "TraderDiaryPrefs";
    private static final String KEY_ALL_TRANSACTIONS = "all_transactions";
    private static final String KEY_MONTHLY_TRANSACTIONS = "monthly_transactions";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        initializeWeekLayouts();
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

    private void initializeViews() {
        tvTotalPnl = findViewById(R.id.tv_total_pnl);
        tvInTradingDays = findViewById(R.id.tv_trading_days);
        tvInAddedOn = findViewById(R.id.tv_added_on);
        tvInProductDays = findViewById(R.id.tv_product_days);
        tvWinningsStruck = findViewById(R.id.tv_winnings_struck);
        tvProfitableDaysCount = findViewById(R.id.tv_profitable_days_count);
        tvMonthYear = findViewById(R.id.tv_month_year);
        layoutMostProfitableDays = findViewById(R.id.layout_most_profitable_days);
        layoutTransactionList = findViewById(R.id.layout_transaction_list);
        fabAdd = findViewById(R.id.fab_add);
        btnPrevMonth = findViewById(R.id.btn_prev_month);
        btnNextMonth = findViewById(R.id.btn_next_month);
    }

    private void initializeWeekLayouts() {
        weekLayouts[0] = findViewById(R.id.layout_week1);
        weekLayouts[1] = findViewById(R.id.layout_week2);
        weekLayouts[2] = findViewById(R.id.layout_week3);
        weekLayouts[3] = findViewById(R.id.layout_week4);
        weekLayouts[4] = findViewById(R.id.layout_week5);
        weekLayouts[5] = findViewById(R.id.layout_week6);
    }

    private void initializeCurrentMonth() {
        currentCalendar = Calendar.getInstance();
        currentMonth = currentCalendar.get(Calendar.MONTH);
        currentYear = currentCalendar.get(Calendar.YEAR);

        updateMonthYearDisplay();
        loadCurrentMonthData();
    }

    private void updateMonthYearDisplay() {
        String monthName = new SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                .format(currentCalendar.getTime());
        tvMonthYear.setText(monthName);

        TextView tvTransactionsHeader = findViewById(R.id.tv_transactions_header);
        tvTransactionsHeader.setText(monthName);

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

        if (nextMonth.after(now) && nextMonth.get(Calendar.MONTH) != now.get(Calendar.MONTH)) {
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

        List<String> monthsWithData = new ArrayList<>(monthlyTransactions.keySet());
        Collections.sort(monthsWithData, Collections.reverseOrder());

        String currentMonthKey = getMonthKey(currentMonth, currentYear);
        if (!monthsWithData.contains(currentMonthKey)) {
            monthsWithData.add(currentMonthKey);
        }

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

    private void showTransactionDetails(int day) {
        Transaction transaction = dailyTransactions.get(day);
        if (transaction == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Transaction Details");

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        String message = String.format("Date: %s\nAmount: ₹ %.2f\nType: %s",
                sdf.format(transaction.getDate()),
                Math.abs(transaction.getAmount()),
                transaction.isProfit() ? "Profit" : "Loss");

        builder.setMessage(message);
        builder.setPositiveButton("OK", null);

        builder.setNegativeButton("Delete", (dialog, which) -> {
            deleteTransaction(transaction, day);
        });

        builder.show();
    }

    private void deleteTransaction(Transaction transaction, int day) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Transaction");
        builder.setMessage("Are you sure you want to delete this transaction?");

        builder.setPositiveButton("Delete", (dialog, which) -> {
            // Remove from all lists
            allTransactions.remove(transaction);

            Calendar cal = Calendar.getInstance();
            cal.setTime(transaction.getDate());
            int month = cal.get(Calendar.MONTH);
            int year = cal.get(Calendar.YEAR);
            String monthKey = getMonthKey(month, year);

            // Remove from monthly transactions
            List<Transaction> monthTransactions = monthlyTransactions.get(monthKey);
            if (monthTransactions != null) {
                monthTransactions.remove(transaction);
                if (monthTransactions.isEmpty()) {
                    monthlyTransactions.remove(monthKey);
                }
            }

            // Remove from dailyTransactions map
            dailyTransactions.remove(day);

            // Clear and reload current month data
            loadCurrentMonthData();

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

        Gson gson = new Gson();
        String transactionsJson = gson.toJson(allTransactions);
        editor.putString(KEY_ALL_TRANSACTIONS, transactionsJson);

        String monthlyTransactionsJson = gson.toJson(monthlyTransactions);
        editor.putString(KEY_MONTHLY_TRANSACTIONS, monthlyTransactionsJson);

        editor.apply();
    }

    private void loadSavedData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Gson gson = new Gson();

        String transactionsJson = prefs.getString(KEY_ALL_TRANSACTIONS, null);
        if (transactionsJson != null) {
            Type transactionListType = new TypeToken<ArrayList<Transaction>>() {}.getType();
            List<Transaction> savedTransactions = gson.fromJson(transactionsJson, transactionListType);
            if (savedTransactions != null) {
                allTransactions = savedTransactions;
                rebuildMonthlyTransactions();
            }
        }

        String monthlyTransactionsJson = prefs.getString(KEY_MONTHLY_TRANSACTIONS, null);
        if (monthlyTransactionsJson != null) {
            Type monthlyTransactionsType = new TypeToken<HashMap<String, List<Transaction>>>() {}.getType();
            Map<String, List<Transaction>> savedMonthlyTransactions = gson.fromJson(monthlyTransactionsJson, monthlyTransactionsType);
            if (savedMonthlyTransactions != null) {
                monthlyTransactions = savedMonthlyTransactions;
                rebuildAllTransactions();
            }
        }

        if (allTransactions.isEmpty()) {
            initializeSampleData();
        }
    }

    private void rebuildMonthlyTransactions() {
        monthlyTransactions.clear();
        for (Transaction transaction : allTransactions) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(transaction.getDate());
            String monthKey = getMonthKey(cal.get(Calendar.MONTH), cal.get(Calendar.YEAR));

            List<Transaction> monthTransactions = monthlyTransactions.get(monthKey);
            if (monthTransactions == null) {
                monthTransactions = new ArrayList<>();
                monthlyTransactions.put(monthKey, monthTransactions);
            }
            monthTransactions.add(transaction);
        }
    }

    private void rebuildAllTransactions() {
        allTransactions.clear();
        for (List<Transaction> monthTransactions : monthlyTransactions.values()) {
            allTransactions.addAll(monthTransactions);
        }
    }

    private void initializeSampleData() {
        Calendar cal = Calendar.getInstance();

        // Add sample transactions for current month
        cal.set(Calendar.DAY_OF_MONTH, 23);
        addSampleTransaction(cal.getTime(), -5136.99, false);

        cal.set(Calendar.DAY_OF_MONTH, 22);
        addSampleTransaction(cal.getTime(), -261.00, false);

        cal.set(Calendar.DAY_OF_MONTH, 19);
        addSampleTransaction(cal.getTime(), 571.00, true);

        cal.set(Calendar.DAY_OF_MONTH, 18);
        addSampleTransaction(cal.getTime(), 1072.00, true);

        cal.set(Calendar.DAY_OF_MONTH, 17);
        addSampleTransaction(cal.getTime(), 1562.00, true);

        cal.set(Calendar.DAY_OF_MONTH, 15);
        addSampleTransaction(cal.getTime(), 450.00, true);

        cal.set(Calendar.DAY_OF_MONTH, 14);
        addSampleTransaction(cal.getTime(), -120.50, false);

        cal.set(Calendar.DAY_OF_MONTH, 10);
        addSampleTransaction(cal.getTime(), 890.75, true);

        // Add some sample data for previous months
        cal.add(Calendar.MONTH, -1);
        cal.set(Calendar.DAY_OF_MONTH, 15);
        addSampleTransaction(cal.getTime(), 1200.50, true);

        saveData();
    }

    private void addSampleTransaction(Date date, double amount, boolean isProfit) {
        Transaction transaction = new Transaction(date, amount, isProfit);
        allTransactions.add(transaction);

        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        String monthKey = getMonthKey(cal.get(Calendar.MONTH), cal.get(Calendar.YEAR));

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
        updateCalendarDisplay();
        updateTransactionList();
    }

    private void updateCalendarDisplay() {
        // Clear all week layouts
        for (LinearLayout weekLayout : weekLayouts) {
            weekLayout.removeAllViews();
            weekLayout.setVisibility(View.VISIBLE);
        }

        // Create a calendar for the first day of the month
        Calendar calendar = (Calendar) currentCalendar.clone();
        calendar.set(Calendar.DAY_OF_MONTH, 1);

        // Get the day of week for the first day (1 = Sunday, 2 = Monday, etc.)
        int firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);

        // Adjust for Monday as first day (if you want Sunday first, remove the -1)
        int startOffset = firstDayOfWeek - Calendar.MONDAY;
        if (startOffset < 0) {
            startOffset += 7;
        }

        // Get number of days in month
        int daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);

        int dayCounter = 1;
        int weekIndex = 0;

        // Add empty cells for days before the first day of month
        for (int i = 0; i < startOffset; i++) {
            TextView emptyView = createEmptyDateView();
            weekLayouts[weekIndex].addView(emptyView);
        }

        // Add date cells for each day of the month
        while (dayCounter <= daysInMonth) {
            // Create date view for current day
            TextView dateView = createDateView(dayCounter);
            weekLayouts[weekIndex].addView(dateView);

            // Move to next day
            dayCounter++;

            // Check if we need to move to next week
            if ((startOffset + dayCounter - 1) % 7 == 0 && dayCounter <= daysInMonth) {
                weekIndex++;
                if (weekIndex >= weekLayouts.length) {
                    // Create additional week if needed
                    createAdditionalWeekLayout(weekIndex);
                }
            }
        }

        // Hide unused week layouts
        for (int i = weekIndex + 1; i < weekLayouts.length; i++) {
            weekLayouts[i].setVisibility(View.GONE);
        }

        // Update calendar colors
        updateCalendarColors();
    }

    private TextView createEmptyDateView() {
        TextView emptyView = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                dpToPx(40),
                1
        );
        params.setMargins(2, 2, 2, 2);
        emptyView.setLayoutParams(params);
        emptyView.setBackgroundColor(Color.TRANSPARENT);
        emptyView.setText("");
        return emptyView;
    }

    private TextView createDateView(final int day) {
        TextView dateView = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                dpToPx(40),
                1
        );
        params.setMargins(2, 2, 2, 2);
        dateView.setLayoutParams(params);
        dateView.setBackgroundResource(R.drawable.date_background);
        dateView.setGravity(android.view.Gravity.CENTER);
        dateView.setText(String.valueOf(day));
        dateView.setTextSize(14);
        dateView.setTextColor(Color.BLACK);

        // Set click listener
        dateView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Transaction t = dailyTransactions.get(day);
                if (t != null) {
                    showTransactionDetails(day);
                } else {
                    // If no transaction, allow adding one
                    showAddTransactionForDay(day);
                }
            }
        });

        return dateView;
    }

    private void createAdditionalWeekLayout(int weekIndex) {
        if (weekIndex >= 5) { // We only have up to week6 in XML
            LinearLayout newWeekLayout = new LinearLayout(this);
            newWeekLayout.setId(View.generateViewId());
            newWeekLayout.setOrientation(LinearLayout.HORIZONTAL);
            newWeekLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            LinearLayout calendarContainer = findViewById(R.id.calendar_container);
            calendarContainer.addView(newWeekLayout);

            // Resize array and add new layout
            LinearLayout[] newArray = new LinearLayout[weekLayouts.length + 1];
            System.arraycopy(weekLayouts, 0, newArray, 0, weekLayouts.length);
            newArray[weekIndex] = newWeekLayout;
            weekLayouts = newArray;
        }
    }

    private void updateCalendarColors() {
        Calendar tempCal = (Calendar) currentCalendar.clone();
        tempCal.set(Calendar.DAY_OF_MONTH, 1);
        int daysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int day = 1; day <= daysInMonth; day++) {
            Transaction t = dailyTransactions.get(day);

            // Find the TextView for this day
            TextView dateView = findDateViewForDay(day);
            if (dateView != null) {
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

    private TextView findDateViewForDay(int day) {
        // Search through all week layouts to find the TextView for this day
        for (LinearLayout weekLayout : weekLayouts) {
            if (weekLayout.getVisibility() == View.VISIBLE) {
                for (int i = 0; i < weekLayout.getChildCount(); i++) {
                    View child = weekLayout.getChildAt(i);
                    if (child instanceof TextView) {
                        TextView textView = (TextView) child;
                        String text = textView.getText().toString();
                        if (!text.isEmpty()) {
                            try {
                                int viewDay = Integer.parseInt(text);
                                if (viewDay == day) {
                                    return textView;
                                }
                            } catch (NumberFormatException e) {
                                // Not a number, continue
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void showAddTransactionForDay(int day) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Transaction for " + getDayWithSuffix(day));

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_transaction, null);
        builder.setView(dialogView);

        EditText etAmount = dialogView.findViewById(R.id.et_amount);
        RadioGroup rgType = dialogView.findViewById(R.id.rg_type);
        TextView tvDate = dialogView.findViewById(R.id.tv_date);
        Button btnSelectDate = dialogView.findViewById(R.id.btn_select_date);

        // Set the date to the selected day
        Calendar selectedCal = (Calendar) currentCalendar.clone();
        selectedCal.set(Calendar.DAY_OF_MONTH, day);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        tvDate.setText(sdf.format(selectedCal.getTime()));

        // Hide date selector since we're setting it automatically
        btnSelectDate.setVisibility(View.GONE);
        tvDate.setEnabled(false);

        builder.setPositiveButton("Add", (dialog, which) -> {
            handleAddTransactionForDay(etAmount, rgType, day);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void handleAddTransactionForDay(EditText etAmount, RadioGroup rgType, int day) {
        String amountStr = etAmount.getText().toString().trim();
        if (amountStr.isEmpty()) {
            Toast.makeText(MainActivity.this, "Please enter amount", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr);
            boolean isProfit = rgType.getCheckedRadioButtonId() == R.id.rb_profit;

            Calendar cal = (Calendar) currentCalendar.clone();
            cal.set(Calendar.DAY_OF_MONTH, day);
            Date transactionDate = cal.getTime();

            addNewTransaction(transactionDate, amount, isProfit, day, currentMonth, currentYear, false);

        } catch (NumberFormatException e) {
            Toast.makeText(MainActivity.this, "Invalid amount", Toast.LENGTH_SHORT).show();
        }
    }

    private String getDayWithSuffix(int day) {
        if (day >= 11 && day <= 13) {
            return day + "th";
        }
        switch (day % 10) {
            case 1: return day + "st";
            case 2: return day + "nd";
            case 3: return day + "rd";
            default: return day + "th";
        }
    }

    private void calculateAndDisplayStats() {
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

        Calendar tempCal = (Calendar) currentCalendar.clone();
        tempCal.set(Calendar.DAY_OF_MONTH, 1);
        int totalDaysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH);

        int tradedDays = dailyTransactions.size();
        int profitableDays = 0;
        int winningStreak = 0;
        int currentStreak = 0;

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

        tvInTradingDays.setText(String.valueOf(totalDaysInMonth));
        tvInAddedOn.setText(String.valueOf(tradedDays));
        tvInProductDays.setText(String.valueOf(profitableDays));
        tvWinningsStruck.setText(String.valueOf(winningStreak));

        tvProfitableDaysCount.setText(String.format("%d/%d Traded Days", profitableDays, tradedDays));
    }

    private void updateMostProfitableDays() {
        layoutMostProfitableDays.removeAllViews();

        List<Transaction> profitableTransactions = new ArrayList<>();
        for (Transaction t : currentMonthTransactions) {
            if (t.isProfit()) {
                profitableTransactions.add(t);
            }
        }

        Collections.sort(profitableTransactions, (t1, t2) ->
                Double.compare(Math.abs(t2.getAmount()), Math.abs(t1.getAmount())));

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

        TextView emojiView = new TextView(this);
        emojiView.setText(isFirst ? "🔴" : "⚪");
        emojiView.setTextSize(12);
        emojiView.setGravity(android.view.Gravity.CENTER);
        emojiView.setWidth(24);
        itemLayout.addView(emojiView);

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

        TextView dateView = new TextView(this);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        dateView.setText(String.format("%s:", sdf.format(transaction.getDate())));
        dateView.setTextSize(14);
        dateView.setTextColor(Color.BLACK);
        dateView.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        itemLayout.addView(dateView);

        TextView amountView = new TextView(this);
        amountView.setText(String.format("₹ %.2f", transaction.getAmount()));
        amountView.setTextSize(14);
        amountView.setTypeface(amountView.getTypeface(), android.graphics.Typeface.BOLD);
        amountView.setTextColor(transaction.isProfit() ?
                Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
        itemLayout.addView(amountView);

        layoutTransactionList.addView(itemLayout);
    }

    private void showAddTransactionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add New Transaction");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_transaction, null);
        builder.setView(dialogView);

        EditText etAmount = dialogView.findViewById(R.id.et_amount);
        RadioGroup rgType = dialogView.findViewById(R.id.rg_type);
        RadioButton rbProfit = dialogView.findViewById(R.id.rb_profit);
        RadioButton rbLoss = dialogView.findViewById(R.id.rb_loss);

        TextView tvDate = dialogView.findViewById(R.id.tv_date);
        Button btnSelectDate = dialogView.findViewById(R.id.btn_select_date);

        Date selectedDate = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        tvDate.setText(sdf.format(selectedDate));

        btnSelectDate.setOnClickListener(v -> showDatePickerDialog(tvDate));

        builder.setPositiveButton("Add", (dialog, which) -> {
            handleAddTransaction(etAmount, rgType, tvDate);
        });

        builder.setNegativeButton("Cancel", null);

        builder.show();
    }

    private void handleAddTransaction(EditText etAmount, RadioGroup rgType, TextView tvDate) {
        String amountStr = etAmount.getText().toString().trim();
        if (amountStr.isEmpty()) {
            Toast.makeText(MainActivity.this, "Please enter amount", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr);
            boolean isProfit = rgType.getCheckedRadioButtonId() == R.id.rb_profit;

            Date transactionDate;
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                transactionDate = sdf.parse(tvDate.getText().toString());
            } catch (Exception e) {
                transactionDate = new Date();
            }

            Calendar cal = Calendar.getInstance();
            cal.setTime(transactionDate);
            int day = cal.get(Calendar.DAY_OF_MONTH);
            int month = cal.get(Calendar.MONTH);
            int year = cal.get(Calendar.YEAR);

            final Date finalTransactionDate = transactionDate;
            final double finalAmount = amount;
            final boolean finalIsProfit = isProfit;
            final int finalDay = day;
            final int finalMonth = month;
            final int finalYear = year;

            // Check if transaction is for current month
            if (month == currentMonth && year == currentYear) {
                // Check if transaction already exists for this day
                if (dailyTransactions.containsKey(day)) {
                    AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(this);
                    confirmBuilder.setTitle("Transaction Exists");
                    confirmBuilder.setMessage("A transaction already exists for this day. Do you want to replace it?");

                    confirmBuilder.setPositiveButton("Replace", (dialog1, which1) -> {
                        addNewTransaction(finalTransactionDate, finalAmount, finalIsProfit, finalDay, finalMonth, finalYear, true);
                    });

                    confirmBuilder.setNegativeButton("Cancel", null);
                    confirmBuilder.show();
                } else {
                    addNewTransaction(finalTransactionDate, finalAmount, finalIsProfit, finalDay, finalMonth, finalYear, false);
                }
            } else {
                AlertDialog.Builder monthBuilder = new AlertDialog.Builder(this);
                monthBuilder.setTitle("Different Month");
                monthBuilder.setMessage("This transaction is for " +
                        new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(transactionDate) +
                        ". Do you want to add it and switch to that month?");

                monthBuilder.setPositiveButton("Add and Switch", (dialog1, which1) -> {
                    addNewTransaction(finalTransactionDate, finalAmount, finalIsProfit, finalDay, finalMonth, finalYear, false);

                    currentCalendar.set(Calendar.MONTH, finalMonth);
                    currentCalendar.set(Calendar.YEAR, finalYear);
                    currentMonth = finalMonth;
                    currentYear = finalYear;

                    updateMonthYearDisplay();
                    loadCurrentMonthData();
                    updateUIForCurrentMonth();
                });

                monthBuilder.setNegativeButton("Add Only", (dialog1, which1) -> {
                    addNewTransaction(finalTransactionDate, finalAmount, finalIsProfit, finalDay, finalMonth, finalYear, false);
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

    private void addNewTransaction(Date date, double amount, boolean isProfit, int day, int month, int year, boolean replaceExisting) {
        Transaction newTransaction = new Transaction(date,
                isProfit ? amount : -amount, isProfit);

        String monthKey = getMonthKey(month, year);
        List<Transaction> monthTransactions = monthlyTransactions.get(monthKey);

        if (monthTransactions == null) {
            monthTransactions = new ArrayList<>();
            monthlyTransactions.put(monthKey, monthTransactions);
        }

        // Remove existing transaction if replacing
        if (replaceExisting) {
            Transaction existing = dailyTransactions.get(day);
            if (existing != null) {
                // Remove from all data structures
                allTransactions.remove(existing);
                monthTransactions.remove(existing);
                dailyTransactions.remove(day);
            }
        } else {
            // Check if transaction for same day exists (for duplicate prevention)
            Transaction existing = null;
            for (Transaction t : monthTransactions) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(t.getDate());
                if (cal.get(Calendar.DAY_OF_MONTH) == day) {
                    existing = t;
                    break;
                }
            }

            if (existing != null) {
                // Remove existing duplicate
                allTransactions.remove(existing);
                monthTransactions.remove(existing);
            }
        }

        // Add new transaction
        allTransactions.add(newTransaction);
        monthTransactions.add(newTransaction);

        // Update current month data if applicable
        if (month == currentMonth && year == currentYear) {
            dailyTransactions.put(day, newTransaction);
            currentMonthTransactions = monthTransactions;
        }

        // Update UI and save data
        updateUIForCurrentMonth();
        saveData();

        Toast.makeText(this, "Transaction added", Toast.LENGTH_SHORT).show();
    }
}