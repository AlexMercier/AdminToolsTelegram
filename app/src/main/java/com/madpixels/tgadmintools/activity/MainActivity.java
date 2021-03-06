package com.madpixels.tgadmintools.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.madpixels.apphelpers.MyLog;
import com.madpixels.apphelpers.MyToast;
import com.madpixels.apphelpers.Sets;
import com.madpixels.apphelpers.UIUtils;
import com.madpixels.apphelpers.Utils;
import com.madpixels.apphelpers.ui.ActivityExtended;
import com.madpixels.tgadmintools.BuildConfig;
import com.madpixels.tgadmintools.Const;
import com.madpixels.tgadmintools.R;
import com.madpixels.tgadmintools.db.DBHelper;
import com.madpixels.tgadmintools.helper.TgH;
import com.madpixels.tgadmintools.helper.TgUtils;
import com.madpixels.tgadmintools.services.ServiceAutoKicker;
import com.madpixels.tgadmintools.services.ServiceBackgroundStarter;
import com.madpixels.tgadmintools.services.ServiceChatTask;
import com.madpixels.tgadmintools.services.ServiceGarbageCollector;
import com.madpixels.tgadmintools.services.ServiceUnbanTask;
import com.madpixels.tgadmintools.utils.Analytics;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TG;
import org.drinkless.td.libcore.telegram.TdApi;

import java.util.Arrays;
import java.util.Locale;
import java.util.MissingResourceException;

import libs.AdHelper;

public class MainActivity extends ActivityExtended {


    TextView tvAdditionalStatus;
    private boolean skipShowActivity = false;

    private Button btnStop, btnGroups;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeLanguage(this);
        setContentView(R.layout.activity_main);
        UIUtils.setToolbar(this, R.id.toolbar);
        AdHelper.setup(this);

        if (savedInstanceState == null) {
            onAppUpgrade();
        }

        tvAdditionalStatus = (TextView) findViewById(R.id.tvAdditionalStatus);

        Button btnClearCache = getView(R.id.btnClearCache);
        btnGroups = getView(R.id.btnGroups);
        btnStop = getView(R.id.btnStop);
        final CheckBox checkBoxWriteLog = (CheckBox) findViewById(R.id.checkBoxWriteLog);

        if (!BuildConfig.DEBUG) {
            btnClearCache.setVisibility(View.GONE);
            checkBoxWriteLog.setVisibility(View.GONE);
        }
        tvAdditionalStatus.setText(R.string.text_checking_auth);
        final TextView textViewAppState = getView(R.id.textViewAppState);

        btnGroups.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(mContext, ActivityGroupsList.class));
            }
        });

        TgH.init(this);
        checkAuth();

        checkBoxWriteLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MyLog.WRITE_TO_FILE = checkBoxWriteLog.isChecked();
                ;
                MyToast.toast(getApplication(), "write is " + (MyLog.WRITE_TO_FILE ? "enabled" : "disabled"));
            }
        });

        btnClearCache.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TgH.clearCache(MainActivity.this);
            }
        });
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v.getTag() == null) {
                    stopApplication(mContext);
                    btnStop.setText(R.string.btn_main_run_app);
                    btnStop.setTag("1");
                    textViewAppState.setText(R.string.text_appstate_stopped);
                    btnGroups.setEnabled(false);
                } else {
                    startApplication(mContext);
                    btnStop.setText(R.string.btn_main_stop_app);
                    textViewAppState.setText(R.string.text_appstate_enabled);
                    btnStop.setTag(null);
                    btnGroups.setEnabled(true);
                }
            }
        });


        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                TgH.clearCache(mContext);
            }
        }, 1300);

        if (BuildConfig.DEBUG) {// for debug write log only
            checkStoragePermissions(this);
        }
    }

    public static boolean checkStoragePermissions(Activity a) {
        if (Build.VERSION.SDK_INT >= 23) {
            int hasReadStorage = a.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (hasReadStorage != PackageManager.PERMISSION_GRANTED) {
                a.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, Const.ACTION_REQUEST_STORAGE_PERMISSION);
                return false;
            }
        }
        return true;
    }

    @SuppressLint("NewApi")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Const.ACTION_REQUEST_STORAGE_PERMISSION && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                MyToast.toast(mContext, "Storage permission granted");
            }

        }
    }

    private void onAppUpgrade() {
        int oldVersion = Sets.getInteger("TgAppVersion", 0);
        if (BuildConfig.VERSION_CODE > oldVersion) {
            Sets.set("TgAppVersion", BuildConfig.VERSION_CODE);
            if (oldVersion > 0) {
                dialogRecentChanges();
            }
        }
    }

    public static void initializeLanguage(Context c) {
        if (c.getString(R.string.lang_code).equals("en")) {
            String pCurrentLang = "en";
            try {
                pCurrentLang = Locale.getDefault().getISO3Country();
            } catch (MissingResourceException e) {
                e.printStackTrace();
            }
            String[] ruLangs = new String[]{"UKR", "KAZ", "BLR"};
            if (Arrays.asList(ruLangs).contains(pCurrentLang)) {
                android.content.res.Resources resource = c.getResources();
                // Change locale settings in the app.
                DisplayMetrics dm = resource.getDisplayMetrics();
                android.content.res.Configuration conf = resource.getConfiguration();
                conf.locale = new Locale("ru");
                resource.updateConfiguration(conf, dm); //force set Russian
            }
        }
    }

    void checkAuth() {
        TgH.sendOnUi(new TdApi.GetAuthState(), new Client.ResultHandler() {
            @Override
            public void onResult(final TdApi.TLObject object) {
                checkAuthState(object);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 100, 0, R.string.action_settings);
        menu.add(0, 101, 0, R.string.action_log);
        menu.add(0, 102, 0, R.string.action_change_user);
        menu.add(0, 103, 0, R.string.action_about_app);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 100:
                startActivity(new Intent(this, ActivitySettings.class));
                break;
            case 101:
                startActivity(new Intent(this, ActivityLogView.class));
                break;
            case 102:
                dialogChangeAccount();
                break;
            case 103:
                dialogAbout();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void dialogChangeAccount() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_logout)
                .setMessage(R.string.dialog_text_logout)
                .setNegativeButton(R.string.btnCancel, null)
                .setPositiveButton(R.string.btnContinue, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        changeAccount();
                    }
                })
                .show();
    }

    private void changeAccount() {
        TgH.sendOnUi(new TdApi.ResetAuth(false), new Client.ResultHandler() {
            @Override
            public void onResult(TdApi.TLObject object) {
                stopApplication(mContext);
                DBHelper.getInstance().dbClose();
                Sets.removeSetting(Const.SETS_PROFILE_ID);
                TgH.init(mContext);
                checkAuth();
            }
        });
    }


    void checkAuthState(TdApi.TLObject object) {
        if (TgUtils.isError(object)) {
            TdApi.Error e = (TdApi.Error) object;
            switch (e.code) {
                case 400:
                    switch (e.message) {
                        case "PHONE_CODE_INVALID":
                            showLoginDialog(TdApi.AuthStateWaitCode.CONSTRUCTOR);
                            MyToast.toast(MainActivity.this, e.message);
                            break;
                        case "PHONE_NUMBER_INVALID":
                            showLoginDialog(TdApi.AuthStateWaitPhoneNumber.CONSTRUCTOR);
                            MyToast.toast(MainActivity.this, e.message);
                            break;
                        case "PASSWORD_HASH_INVALID":
                            showLoginDialog(TdApi.AuthStateWaitPassword.CONSTRUCTOR);
                            MyToast.toast(MainActivity.this, R.string.error_auth_pin_incorrect);
                            break;
                        default:
                            Analytics.sendReport("AuthError", e.message, e.toString());
                            showInstantErrorDialog(e);
                            break;
                    }
                    break;
                default:
                    Analytics.sendReport("AuthError", e.message, e.toString());
                    showInstantErrorDialog(e);

                    break;
            }
            return;
        }

        TdApi.AuthState authState = (TdApi.AuthState) object;

        switch (authState.getConstructor()) {
            case TdApi.AuthStateWaitCode.CONSTRUCTOR:
                TdApi.AuthStateWaitCode stateWaitCode = (TdApi.AuthStateWaitCode) authState;
                boolean isRegistered = stateWaitCode.isRegistered;
                if (!isRegistered) {
                    showInstantErrorDialog(null);
                    return;
                }

                tvAdditionalStatus.setText("Required sms code...");
                showLoginDialog(authState.getConstructor(), stateWaitCode.nextCodeType);
                break;
            case TdApi.AuthStateWaitPhoneNumber.CONSTRUCTOR:
                showLoginDialog(authState.getConstructor());
                break;
            case TdApi.AuthStateWaitPassword.CONSTRUCTOR:
                tvAdditionalStatus.setText("Required login password...");
                showLoginDialog(authState.getConstructor());
                break;
            case TdApi.AuthStateOk.CONSTRUCTOR:
                tvAdditionalStatus.setText("Status: Authorized");
                onLoginSucces();
                break;
        }
    }


    void onLoginSucces() {
        TgH.getProfile(new Client.ResultHandler() {
            @Override
            public void onResult(final TdApi.TLObject object) {
                if (!skipShowActivity) /* if skipShowActivity then no show activity */
                    startActivity(new Intent(mContext, ActivityGroupsList.class));
                ServiceUnbanTask.registerTask(mContext);
                ServiceAutoKicker.registerTask(mContext);
                ServiceChatTask.start(mContext);
                ServiceGarbageCollector.start(mContext);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TdApi.User me = (TdApi.User) object;
                        tvAdditionalStatus.setText("Status: Authorized\n" +
                                "Profile: "+me.firstName+" "+me.lastName+"\n" +
                                "id: "+me.id +
                                (me.username.isEmpty()?"":"\n@"+me.username));
                    }
                });
            }
        });
    }

    public static void stopApplication(Context c) {
        TG.stopClient();
        ServiceChatTask.stop(c);
        ServiceAutoKicker.stop(c);
        ServiceUnbanTask.unregister(c);
        ServiceBackgroundStarter.stop(c);
    }

    public static void startApplication(final Context mContext) {
        TgH.init(mContext);
        TgH.sendOnUi(new TdApi.GetAuthState(), new Client.ResultHandler() {
            @Override
            public void onResult(final TdApi.TLObject object) {
                ServiceChatTask.start(mContext);
                ServiceAutoKicker.registerTask(mContext);
                ServiceUnbanTask.registerTask(mContext);
            }
        });

    }

    private void showLoginDialog(final int action) {
        showLoginDialog(action, null);
    }

    private void showLoginDialog(final int action, final TdApi.AuthCodeType nextAuthCodeType) {
        final View view = getLayoutInflater().inflate(R.layout.dialog_login, null);
        final AlertDialog d = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(R.string.dialog_title_auth)
                .setView(view)
                .show();


        final Button login = (Button) view.findViewById(R.id.bLogin);
        final Button btnCancel = (Button) view.findViewById(R.id.btnCancel);
        final EditText eEditText = (EditText) view.findViewById(R.id.ePhone);
        final TextView tvResendSMS = UIUtils.getView(view, R.id.tvResendSMS);
        if (nextAuthCodeType == null)
            tvResendSMS.setVisibility(View.GONE);

        switch (action) {
            case TdApi.AuthStateWaitPhoneNumber.CONSTRUCTOR:
                eEditText.setHint(R.string.auth_phone_hint);
                eEditText.setInputType(InputType.TYPE_CLASS_PHONE);
                break;
            case TdApi.AuthStateWaitCode.CONSTRUCTOR:
                eEditText.setHint(R.string.auth_confirmcode_hint);
                eEditText.setInputType(InputType.TYPE_CLASS_PHONE);
                break;
            case TdApi.AuthStateWaitPassword.CONSTRUCTOR:
                eEditText.setHint(R.string.auth_cloud_pass_hint);
                break;
            default:
                // Analytics.sendReport("LogAction", action.name(), "");
                break;
        }

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TgH.send(new TdApi.ResetAuth(true));
                finish();
            }
        });

        if (nextAuthCodeType != null) {
            if (nextAuthCodeType.getConstructor() == TdApi.AuthCodeTypeSms.CONSTRUCTOR){
                tvResendSMS.setText(R.string.btnSendNewSmsCode);
            }
            else if (nextAuthCodeType.getConstructor() == TdApi.AuthCodeTypeCall.CONSTRUCTOR)
                tvResendSMS.setText(R.string.btnRequestAuthCall);

            tvResendSMS.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    login.setEnabled(false);
                    eEditText.setEnabled(false);
                    v.setEnabled(false);
                    login.setText(R.string.label_loading);
                    TgH.sendOnUi(new TdApi.ResendAuthCode(), new Client.ResultHandler() {
                        @Override
                        public void onResult(TdApi.TLObject object) {
                            d.dismiss();
                            checkAuthState(object);
                        }
                    });
                }
            });
        }

        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = eEditText.getText().toString();
                if(text.isEmpty()){
                    showLoginDialog(action, nextAuthCodeType);
                    MyToast.toast(mContext, R.string.toast_auth_input_is_empty);
                }

                TdApi.TLFunction func = null;
                switch (action) {
                    case TdApi.AuthStateWaitPhoneNumber.CONSTRUCTOR:
                        func = new TdApi.SetAuthPhoneNumber(text, true, true);
                        break;
                    case TdApi.AuthStateWaitCode.CONSTRUCTOR:
                        func = new TdApi.CheckAuthCode(text, null, null);
                        break;
                    case TdApi.AuthStateWaitPassword.CONSTRUCTOR:
                        func = new TdApi.CheckAuthPassword(text);
                        break;
                }

                //Client c = TgH.TG();
                eEditText.setEnabled(false);
                login.setText(R.string.label_loading);
                login.setEnabled(false);
                TgH.send(func, new Client.ResultHandler() {
                    @Override
                    public void onResult(final TdApi.TLObject object) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                d.dismiss();
                                checkAuthState(object);
                            }
                        });
                    }
                });
            }
        });

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                eEditText.requestFocus();
                UIUtils.showSoftKeyboard(eEditText);
            }
        }, 500);
    }


    private void showInstantErrorDialog(TdApi.Error e) {
        TgH.send(new TdApi.ResetAuth());// cancel current auth
        String error_msg;
        if (e != null)
            error_msg = "Error code: " + e.code + "\nError message: " + e.message + "\n" +
                    "Try again?";
        else
            error_msg = getString(R.string.error_phone_not_registered);


        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle("Auth error")
                .setMessage(error_msg)
                .setPositiveButton("Login", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showLoginDialog(TdApi.AuthStateWaitPhoneNumber.CONSTRUCTOR);
                    }
                })
                .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .show();
    }


    void dialogAbout() {
        View v = UIUtils.inflate(mContext, R.layout.dialog_about);
        TextView version = UIUtils.getView(v, R.id.tvVersion);
        TextView tvReleaseDate = UIUtils.getView(v, R.id.tvReleaseDate);
        TextView tvRateUs = UIUtils.getView(v, R.id.tvRateUs);
        TextView tvContact = UIUtils.getView(v, R.id.tvContact);

        version.setText("v" + BuildConfig.VERSION_NAME);
        tvReleaseDate.setText(R.string.buildDate);

        View.OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.tvContact:
                        Utils.openUrl("https://telegram.me/TgAndroAdminToolsBot", mContext);
                        break;
                    case R.id.tvRateUs:
                        Utils.openStore(mContext);
                        break;
                }
            }
        };

        UIUtils.setBatchClickListener(onClickListener, tvRateUs, tvContact);

        new AlertDialog.Builder(mContext)
                .setTitle("About")
                .setView(v)
                .setNegativeButton("Close", null)
                .show();

    }

    void dialogRecentChanges() {
        skipShowActivity = true;
        new AlertDialog.Builder(mContext)
                .setTitle("Changelog")
                .setMessage(getString(R.string.dialog_recent_changes, BuildConfig.VERSION_NAME))
                .setNegativeButton(R.string.btnClose, null)
                .show();
    }


}
