/*
 * This sample code is a preliminary draft for illustrative purposes only and not subject to any license granted by Wincor Nixdorf.
 * The sample code is provided “as is” and Wincor Nixdorf assumes no responsibility for errors or omissions of any kind out of the
 * use of such code by any third party.
 */
package com.aevi.chatable;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.JavascriptInterface;
import android.widget.TextView;
import android.widget.Toast;

import com.aevi.configuration.TerminalConfiguration;
import com.aevi.helpers.CompatibilityException;
import com.aevi.helpers.ServiceState;
import com.aevi.helpers.services.AeviServiceConnectionCallback;
import com.aevi.payment.PaymentAppConfiguration;
import com.aevi.payment.PaymentAppConfigurationRequest;
import com.aevi.payment.PaymentRequest;
import com.aevi.payment.TransactionResult;
import com.aevi.printing.PrintService;
import com.aevi.printing.PrintServiceProvider;
import com.aevi.printing.model.Alignment;
import com.aevi.printing.model.PrintPayload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.Currency;

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String PAYMENT_APPLICATION_NOT_FOUND_ERROR = "Payment Application is not installed or your application has insufficient rights to access it.\nThis application will now exit.";

    private PaymentAppConfiguration paymentAppConfiguration = null;

    private final MainActivity.JavascriptBridge javascriptBridge = new JavascriptBridge();

    private String currentPage;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // Check if TerminalServices are installed
        try {
            if (TerminalConfiguration.isTerminalServicesInstalled(this) == false) {
                showExitDialog("Terminal Services is not installed or installed incorrectly.\nThis application will now exit.");
                return;
            }
        } catch(CompatibilityException e) {
            showExitDialog(e.getMessage() + "\nThis application will now exit.");
            return;
        }

        // Ensure the payment application/simulator is installed. If not, present an alert dialog
        // to the user and exit.
        ServiceState paymentAppState = PaymentAppConfiguration.getPaymentApplicationStatus(this);
        Log.d(TAG, "Payment App State: " + paymentAppState);

        if (paymentAppState == ServiceState.NOT_INSTALLED) {
            showExitDialog("A payment application is not installed.\n This application will now exit.");
        } else if (paymentAppState == ServiceState.NO_PERMISSION) {
            showExitDialog("A payment application installed but this App does not have the permission to use it.\n This application will now exit.");
        } else if (paymentAppState == ServiceState.UNAVAILABLE) {
            showExitDialog("The payment application is unavailable.\n This application will now exit.");
        } else {
            // Get the Payment App Configuration
            fetchPaymentAppConfiguration();
        }

        webView = (WebView) findViewById(R.id.webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setUseWideViewPort(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setAppCacheEnabled(true);

        webView.addJavascriptInterface(javascriptBridge, "Bridge");
        webView.loadDataWithBaseURL("file:///android_asset/", getFileFromApplicationResources("index.html"), "text/html", "utf-8", "");
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "back button pressed");

        if (!currentPage.equals("/")) {
            javascriptBridge.navigate("/");
        } else {
            finish();
        }
    }

    private String getFileFromApplicationResources(String url) {

        try {
            InputStream htmlStream = getApplicationContext().getAssets().open(url);
            Reader is = new BufferedReader(new InputStreamReader(htmlStream, "UTF8"));

            final char[] buffer = new char[1024];
            StringBuilder out = new StringBuilder();
            int read;
            do {
                read = is.read(buffer, 0, buffer.length);
                if (read > 0) {
                    out.append(buffer, 0, read);
                }
            } while (read >= 0);

            return out.toString();

        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "UnsupportedEncodingException", e);
            return "";
        } catch (IOException e) {
            Log.e(TAG, "IO Exception", e);
            return "";
        }
    }

    private void showExitDialog(String messageStr) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        TextView textView = new TextView(this);
        textView.setText(messageStr);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        builder.setView(textView);
        builder.setPositiveButton("Ok", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        }).setCancelable(false);
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * fetch the Payment Configuration
     */
    private void fetchPaymentAppConfiguration() {
        startActivityForResult(PaymentAppConfigurationRequest.createIntent(), 0);
    }

    /**
     * Called by Android when the control returns to this activity.
     *
     * @param requestCode the code associated with the request (0)
     * @param resultCode  the Intent result code
     * @param data        the result data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 0) {

                // Save the Payment Configuration in the Application Object
                paymentAppConfiguration = PaymentAppConfiguration.fromIntent(data);

                Log.d(TAG, "PaymentAppConfiguration retrieved. Currency code is: " + paymentAppConfiguration.getDefaultCurrency().getCurrencyCode());
            } else if (requestCode > 0) {
                TransactionResult transactionResult = TransactionResult.fromIntent(data);

                switch (transactionResult.getTransactionStatus()) {
                    case APPROVED:
                        javascriptBridge.navigate("/success/" + requestCode);
                        break;
                    default:
                        javascriptBridge.navigate("/failure/" + transactionResult.getTransactionStatus().toString() + "/" + transactionResult.getTransactionErrorCode().toString());
                        break;
                }
            }
        } else {
            showExitDialog("There was a problem obtaining the PaymentAppConfiguration object.\n This application will now exit.");
        }
    }

    private class JavascriptBridge {

        private final String tag = JavascriptBridge.class.getSimpleName();

        @JavascriptInterface
        public void buyTicket(String movieId, String amount) {
            BigDecimal parsedAmount = new BigDecimal(amount);

            Log.d(tag, "Creating a payment request for movieId:" + movieId + ", amount:" + parsedAmount);

            PaymentRequest paymentRequest = new PaymentRequest(parsedAmount);
            startActivityForResult(paymentRequest.createIntent(), Integer.parseInt(movieId));
        }

        @JavascriptInterface
        public void navigate(final String fragment) {
            Log.d(tag, "Navigating to " + fragment);

            currentPage = fragment;

            webView.post(new Runnable() {
                public void run() {
                    webView.loadUrl("javascript:location.hash='#" + fragment + "'");
                }
            });
        }

        @JavascriptInterface
        public void enableScroll() {
            webView.setVerticalScrollBarEnabled(true);
            webView.setHorizontalScrollBarEnabled(true);
            webView.setOnTouchListener(null);
        }

        @JavascriptInterface
        public void disableScroll() {
            webView.setVerticalScrollBarEnabled(false);
            webView.setHorizontalScrollBarEnabled(false);
            webView.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent event) {
                    return (event.getAction() == MotionEvent.ACTION_MOVE);
                }
            });
        }

        @JavascriptInterface
        public void exit() {
            finish();
        }

        @JavascriptInterface
        public void log(String message) {
            Log.i(TAG, message);
        }

        @JavascriptInterface
        public String currencyCode() {
            if (paymentAppConfiguration != null) {
                return paymentAppConfiguration.getDefaultCurrency().getCurrencyCode();
            } else {
                return Currency.getInstance("XXX").getCurrencyCode();
            }
        }

        @JavascriptInterface
        public String currencySymbol() {
            if (paymentAppConfiguration != null) {
                return TextUtils.htmlEncode(paymentAppConfiguration.getDefaultCurrency().getSymbol());
            } else {
                return TextUtils.htmlEncode(Currency.getInstance("XXX").getSymbol());
            }
        }

        @JavascriptInterface
        public boolean isAeviDevice() {
            boolean result = TerminalConfiguration.getTerminalConfiguration(MainActivity.this).isAeviDevice();
            Log.d(TAG, "isAeviDevice() returns: " + result);
            return result;
        }

        @JavascriptInterface
        public void printTicket(final String title) {

            Log.d(tag, "Printing ticket for event:" + title);

            webView.post(new Runnable() {
                @Override
                public void run() {
                    final PrintServiceProvider printServiceProvider = new PrintServiceProvider(getBaseContext());
                    printServiceProvider.connect(new AeviServiceConnectionCallback<PrintService>() {
                        @Override
                        public void onConnect(PrintService service) {

                            if (service == null) {
                                Toast.makeText(getBaseContext(), "Printer service failed to open", Toast.LENGTH_LONG).show();
                                return;
                            }

                            PrintPayload ticket = new PrintPayload();
                            ticket.append(title).align(Alignment.CENTER);
                            ticket.appendEmptyLine();

                            BitmapFactory.Options bitmapFactoryOptions = service.getDefaultPrinterSettings().asBitmapFactoryOptions();
                            Bitmap logo = BitmapFactory.decodeResource(getResources(), R.drawable.qr_code, bitmapFactoryOptions);
                            ticket.append(logo).contrastLevel(100).align(Alignment.CENTER);

                            ticket.append("Please show your ticket").align(Alignment.CENTER);
                            ticket.append("at the entrance of the venue").align(Alignment.CENTER);

                            ticket.appendEmptyLine();
                            ticket.appendEmptyLine();
                            ticket.appendEmptyLine();

                            try {
                                int result = service.print(ticket);
                                if (result <= 0) {
                                    // handle print job failed here
                                    Toast.makeText(getBaseContext(), "Failed to print ticket. Printer unavailable", Toast.LENGTH_LONG).show();
                                }
                            } catch(Exception e) {
                                Toast.makeText(getBaseContext(), "Error while attempting to print ticket: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            });
        }
    }
}